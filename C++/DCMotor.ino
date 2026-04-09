//DCMotor.ino
#include <WiFi.h>
#include <esp_now.h>

//Motor pins (L298N / similar) 
//Motor A
static const int motor1Pin1 = 27;   //IN1
static const int motor1Pin2 = 26;   //IN2
static const int enable1Pin = 14;   //ENA (PWM)

//PWM settings
static const uint32_t PWM_FREQ = 30000;
static const uint8_t  PWM_RES  = 8;     //0..255
static uint8_t autoDuty = 200;          //speed used in AUTO

//Adjustable AUTO threshold (default)
static volatile float tempThresholdC = 20.0;

//ESP-NOW packets (must match CYD) 
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

//CYD -> Motor: sensor telemetry
typedef struct __attribute__((packed)) {
  uint8_t type;            //PKT_SENSOR
  float temperatureC;
  float humidity;
  uint32_t seq;
} sensor_packet_t;

//CYD -> Motor: motor command
typedef struct __attribute__((packed)) {
  uint8_t type;            //PKT_MOTOR
  uint32_t seq;
  int8_t dir;              //-1=REV, 0=STOP, 1=FWD
  uint8_t speed;           //0..255
} motor_packet_t;

//CYD -> Motor: mode switch
typedef struct __attribute__((packed)) {
  uint8_t type;            //PKT_MODE
  uint32_t seq;
  uint8_t mode;            //0=AUTO, 1=MANUAL
} mode_packet_t;

//CYD -> Motor: threshold update
typedef struct __attribute__((packed)) {
  uint8_t type;            //PKT_THRESHOLD
  uint32_t seq;
  float tempThresholdC;
} threshold_packet_t;

//Motor -> CYD: ACK
typedef struct __attribute__((packed)) {
  uint8_t type;            //PKT_ACK
  uint32_t seq;
} ack_packet_t;

//Track last sender MAC so we can ACK back
static uint8_t lastSenderMac[6] = {0};
static bool haveSender = false;

//Track sensor values (latest)
static volatile float lastTemp = NAN;
static volatile float lastHum  = NAN;
static volatile uint32_t lastSeq = 0;

//Override mode
static volatile ControlMode mode = MODE_AUTO;

//Last manual command
static volatile int8_t manualDir = 0;
static volatile uint8_t manualSpeed = 0;

//Optional failsafe: stop motor if no packets for X seconds
static unsigned long lastPacketMs = 0;
static const unsigned long FAILSAFE_MS = 15000;

//Motor control helpers 
void motorStop() {
  digitalWrite(motor1Pin1, LOW);
  digitalWrite(motor1Pin2, LOW);
  ledcWrite(enable1Pin, 0);
}

void motorForward(uint8_t duty) {
  digitalWrite(motor1Pin1, HIGH);
  digitalWrite(motor1Pin2, LOW);
  ledcWrite(enable1Pin, duty);
}

void motorReverse(uint8_t duty) {
  digitalWrite(motor1Pin1, LOW);
  digitalWrite(motor1Pin2, HIGH);
  ledcWrite(enable1Pin, duty);
}

void applyAutoControl(float tempC) {
  if (tempC >= tempThresholdC) {
    motorForward(autoDuty);
  } else {
    motorStop();
  }
}

void applyManualControl(int8_t dir, uint8_t speed) {
  if (dir == 1) motorForward(speed);
  else if (dir == -1) motorReverse(speed);
  else motorStop();
}

static void printMac(const uint8_t* mac) {
  Serial.printf("%02X:%02X:%02X:%02X:%02X:%02X",
                mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

//ESP-NOW helpers 
static void ensurePeerForSender(const uint8_t* src) {
  if (!haveSender || memcmp(lastSenderMac, src, 6) != 0) {
    memcpy(lastSenderMac, src, 6);
    haveSender = true;

    esp_now_peer_info_t peer = {};
    memcpy(peer.peer_addr, src, 6);
    peer.channel = 0;  // auto
    peer.encrypt = false;

    esp_now_del_peer(src); // ignore errors
    esp_err_t addRes = esp_now_add_peer(&peer);
    Serial.print("Added/updated sender peer: ");
    Serial.println((int)addRes);
  }
}

static void sendAck(const uint8_t* dst, uint32_t seq) {
  ack_packet_t ack;
  ack.type = PKT_ACK;
  ack.seq = seq;

  esp_err_t res = esp_now_send(dst, (const uint8_t*)&ack, sizeof(ack));
  Serial.print("[ACK] sent seq ");
  Serial.print(seq);
  Serial.print(" res=");
  Serial.println((int)res);
}

//ESP-NOW receive callback
void onDataRecv(const esp_now_recv_info_t* recv_info, const uint8_t* incomingData, int len) {
  if (!recv_info || !recv_info->src_addr) return;
  const uint8_t* src = recv_info->src_addr;

  lastPacketMs = millis();

  if (len < 1) return;
  uint8_t type = incomingData[0];

  ensurePeerForSender(src);

  if (type == PKT_SENSOR) {
    if (len != (int)sizeof(sensor_packet_t)) {
      Serial.print("Unexpected SENSOR size: ");
      Serial.println(len);
      return;
    }

    sensor_packet_t pkt;
    memcpy(&pkt, incomingData, sizeof(pkt));

    lastTemp = pkt.temperatureC;
    lastHum  = pkt.humidity;
    lastSeq  = pkt.seq;

    Serial.print("SENSOR From ");
    printMac(src);
    Serial.print(" | Seq: ");
    Serial.print(pkt.seq);
    Serial.print(" | Temp: ");
    Serial.print(pkt.temperatureC, 2);
    Serial.print(" C | Hum: ");
    Serial.print(pkt.humidity, 2);
    Serial.println(" %");

    sendAck(src, pkt.seq);

    if (mode == MODE_AUTO) {
      applyAutoControl(pkt.temperatureC);
    }
    return;
  }

  if (type == PKT_MOTOR) {
    if (len != (int)sizeof(motor_packet_t)) {
      Serial.print("Unexpected MOTOR size: ");
      Serial.println(len);
      return;
    }

    motor_packet_t pkt;
    memcpy(&pkt, incomingData, sizeof(pkt));

    Serial.print("MOTOR From ");
    printMac(src);
    Serial.print(" | Seq: ");
    Serial.print(pkt.seq);
    Serial.print(" | Dir: ");
    Serial.print((int)pkt.dir);
    Serial.print(" | Speed: ");
    Serial.println(pkt.speed);

    sendAck(src, pkt.seq);

    //Motor command implies MANUAL override
    mode = MODE_MANUAL;
    manualDir = pkt.dir;
    manualSpeed = pkt.speed;

    applyManualControl(pkt.dir, pkt.speed);
    return;
  }

  if (type == PKT_MODE) {
    if (len != (int)sizeof(mode_packet_t)) {
      Serial.print("Unexpected MODE size: ");
      Serial.println(len);
      return;
    }

    mode_packet_t pkt;
    memcpy(&pkt, incomingData, sizeof(pkt));

    sendAck(src, pkt.seq);

    mode = (pkt.mode == 1) ? MODE_MANUAL : MODE_AUTO;

    Serial.print("MODE set to: ");
    Serial.println(mode == MODE_MANUAL ? "MANUAL" : "AUTO");

    if (mode == MODE_AUTO && !isnan(lastTemp)) applyAutoControl(lastTemp);
    if (mode == MODE_MANUAL) applyManualControl(manualDir, manualSpeed);
    return;
  }

  if (type == PKT_THRESHOLD) {
    if (len != (int)sizeof(threshold_packet_t)) {
      Serial.print("Unexpected THRESH size: ");
      Serial.println(len);
      return;
    }

    threshold_packet_t pkt;
    memcpy(&pkt, incomingData, sizeof(pkt));

    sendAck(src, pkt.seq);

    tempThresholdC = pkt.tempThresholdC;

    Serial.print("AUTO threshold set to: ");
    Serial.println(tempThresholdC, 2);

    if (mode == MODE_AUTO && !isnan(lastTemp)) applyAutoControl(lastTemp);
    return;
  }

  Serial.print("Unknown packet type: ");
  Serial.println(type);
}

void setup() {
  Serial.begin(115200);
  //Motor pins
  pinMode(motor1Pin1, OUTPUT);
  pinMode(motor1Pin2, OUTPUT);
  pinMode(enable1Pin, OUTPUT);

  bool ok = ledcAttach(enable1Pin, PWM_FREQ, PWM_RES);
  Serial.print("LEDC attach: ");
  Serial.println(ok ? "OK" : "FAIL");
  motorStop();

  //ESP-NOW
  WiFi.mode(WIFI_STA);

  Serial.print("Receiver STA MAC: ");
  Serial.println(WiFi.macAddress());

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }

  esp_now_register_recv_cb(onDataRecv);

  Serial.println("ESP-NOW receiver ready (AUTO/MANUAL + threshold + ACK)");
  Serial.print("Default threshold: ");
  Serial.println(tempThresholdC, 1);
}

void loop() {
  if (lastPacketMs != 0 && (millis() - lastPacketMs > FAILSAFE_MS)) {
    motorStop();
  }
  delay(50);
}
