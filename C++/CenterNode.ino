//CenterNode.ino
#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <esp_now.h>
#include <FirebaseClient.h>

//Wi‑Fi
#define WIFI_SSID       "YOUR_WIFI_SSID"
#define WIFI_PASSWORD   "YOUR_WIFI_PASSWORD"

//Firebase
#define WEB_API_KEY     "YOUR_FIREBASE_WEB_API_KEY"
#define DATABASE_URL    "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com/"
#define USER_EMAIL      "YOUR_FIREBASE_AUTH_EMAIL"
#define USER_PASS       "YOUR_FIREBASE_AUTH_PASSWORD"

//Packet Types
enum PacketType : uint8_t {
  PKT_SENSOR    = 1,
  PKT_MOTOR     = 2,
  PKT_ACK       = 3,
  PKT_MODE      = 4,
  PKT_THRESHOLD = 5
};

enum ControlMode : uint8_t {
  MODE_AUTO   = 0,
  MODE_MANUAL = 1
};

typedef struct __attribute__((packed)) {
  uint8_t type;
  float temperatureC;
  float humidity;
  uint32_t seq;
} sensor_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  int8_t dir;
  uint8_t speed;
} motor_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  uint8_t mode;
} mode_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
  float tempThresholdC;
} threshold_packet_t;

typedef struct __attribute__((packed)) {
  uint8_t type;
  uint32_t seq;
} ack_packet_t;

//ESP‑NOW peers
uint8_t MOTOR_MAC[] = {0xF5, 0x2D, 0xC9, 0x79, 0x9F, 0x54};

static uint8_t lastRecvSenderMac[6] = {0};
static bool haveSender = false;

//Shared state
volatile bool sensorUpdated = false;
volatile bool commandForwardRequested = false;

sensor_packet_t latestSensor = {PKT_SENSOR, NAN, NAN, 0};

uint8_t forwardBuffer[32];
int forwardLen = 0;

unsigned long lastFirebasePushMs = 0;
const unsigned long firebasePushIntervalMs = 5000;

//Firebase
void processData(AsyncResult &aResult);

UserAuth user_auth(WEB_API_KEY, USER_EMAIL, USER_PASS);
FirebaseApp app;
WiFiClientSecure ssl_client;
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
RealtimeDatabase Database;

//Helpers
void addOrUpdatePeer(const uint8_t* mac) {
  esp_now_del_peer(mac);

  esp_now_peer_info_t peer = {};
  memcpy(peer.peer_addr, mac, 6);
  peer.channel = WiFi.channel();
  peer.encrypt = false;

  esp_err_t res = esp_now_add_peer(&peer);
  Serial.printf("[ESP-NOW] add peer %02X:%02X:%02X:%02X:%02X:%02X -> %s\n",
                mac[0], mac[1], mac[2], mac[3], mac[4], mac[5],
                (res == ESP_OK ? "OK" : "FAIL"));
}

void ensurePeerForSender(const uint8_t* src) {
  if (!haveSender || memcmp(lastRecvSenderMac, src, 6) != 0) {
    memcpy(lastRecvSenderMac, src, 6);
    haveSender = true;
    addOrUpdatePeer(src);
  }
}

void sendEspNowAck(const uint8_t* dst, uint32_t seq) {
  ack_packet_t ack;
  ack.type = PKT_ACK;
  ack.seq = seq;

  esp_err_t res = esp_now_send(dst, (const uint8_t*)&ack, sizeof(ack));
  Serial.printf("[ESP-NOW] ACK seq=%lu res=%d\n", (unsigned long)seq, (int)res);
}

//ESP‑NOW callback
void onDataRecv(const esp_now_recv_info_t* recv_info, const uint8_t* incomingData, int len) {
  if (!recv_info || !recv_info->src_addr || len < 1) return;

  const uint8_t* src = recv_info->src_addr;
  uint8_t type = incomingData[0];

  ensurePeerForSender(src);

  if (type == PKT_SENSOR) {
    if (len != sizeof(sensor_packet_t)) return;

    sensor_packet_t pkt;
    memcpy(&pkt, incomingData, sizeof(pkt));

    latestSensor = pkt;
    sensorUpdated = true;

    sendEspNowAck(src, pkt.seq);
    return;
  }

  if (type == PKT_MOTOR || type == PKT_MODE || type == PKT_THRESHOLD) {
    if (len > (int)sizeof(forwardBuffer)) return;

    memcpy(forwardBuffer, incomingData, len);
    forwardLen = len;
    commandForwardRequested = true;

    sendEspNowAck(src, 0);
    return;
  }
}

//Firebase callback
void processData(AsyncResult &aResult) {
  if (!aResult.isResult()) return;

  if (aResult.isEvent()) {
    Firebase.printf("[Firebase event] task=%s msg=%s code=%d\n",
                    aResult.uid().c_str(),
                    aResult.eventLog().message().c_str(),
                    aResult.eventLog().code());
  }

  if (aResult.isDebug()) {
    Firebase.printf("[Firebase debug] task=%s msg=%s\n",
                    aResult.uid().c_str(),
                    aResult.debug().c_str());
  }

  if (aResult.isError()) {
    Firebase.printf("[Firebase error] task=%s msg=%s code=%d\n",
                    aResult.uid().c_str(),
                    aResult.error().message().c_str(),
                    aResult.error().code());
  }

  if (aResult.available()) {
    Firebase.printf("[Firebase ok] task=%s payload=%s\n",
                    aResult.uid().c_str(),
                    aResult.c_str());
  }
}

//Firebase push
void pushLatestSensorToFirebase() {
  if (!app.ready()) return;
  if (isnan(latestSensor.temperatureC) || isnan(latestSensor.humidity)) return;

  Database.set<float>(aClient, "/live/temperatureC", latestSensor.temperatureC, processData, "setTemp");
  Database.set<float>(aClient, "/live/humidity", latestSensor.humidity, processData, "setHum");
  Database.set<int>(aClient, "/live/seq", (int)latestSensor.seq, processData, "setSeq");
  Database.set<unsigned long>(aClient, "/live/updatedMs", millis(), processData, "setUpdatedMs");

  Serial.printf("[RTDB] temp=%.2f hum=%.2f seq=%lu\n",
                latestSensor.temperatureC,
                latestSensor.humidity,
                (unsigned long)latestSensor.seq);
}

//Setup
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\nCenter Node booting...");

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Connecting Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }

  Serial.println();
  Serial.print("Wi-Fi connected, IP: ");
  Serial.println(WiFi.localIP());
  Serial.print("Wi-Fi channel: ");
  Serial.println(WiFi.channel());

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }

  esp_now_register_recv_cb(onDataRecv);
  addOrUpdatePeer(MOTOR_MAC);

  Serial.println("ESP-NOW ready");

  ssl_client.setInsecure();
  ssl_client.setConnectionTimeout(1000);
  ssl_client.setHandshakeTimeout(5);

  initializeApp(aClient, app, getAuth(user_auth), processData, "authTask");
  app.getApp<RealtimeDatabase>(Database);
  Database.url(DATABASE_URL);

  Serial.println("Firebase init started");
}

//Loop
void loop() {
  app.loop();

  if (sensorUpdated) {
    sensorUpdated = false;
    Serial.printf("[SENSOR] seq=%lu temp=%.2fC hum=%.2f%%\n",
                  (unsigned long)latestSensor.seq,
                  latestSensor.temperatureC,
                  latestSensor.humidity);
  }

  if (commandForwardRequested) {
    commandForwardRequested = false;

    esp_err_t res = esp_now_send(MOTOR_MAC, forwardBuffer, forwardLen);
    Serial.printf("[FORWARD] len=%d res=%d\n", forwardLen, (int)res);
  }

  unsigned long now = millis();
  if (now - lastFirebasePushMs >= firebasePushIntervalMs) {
    lastFirebasePushMs = now;
    pushLatestSensorToFirebase();
  }

  delay(10);
}
