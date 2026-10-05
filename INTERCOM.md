# RoomCall 음성 인터폰

같은 Wi-Fi의 방 스마트폰에 수신 버튼 없이 연결하는 양방향 음성 인터폰이다.
메시지 호출 기능, 메시지 TCP 서버(5050), 녹음 메시지 재생 및 해당 버튼은 제거했다.
NSD 이름 `RoomCall Receiver`, 타입 `_roomcall._tcp.`는 유지하며 TCP 통화 포트 5051을 광고한다.
추가 dependency나 인터넷 서버는 사용하지 않는다.

## 메인 화면

첨부 디자인을 바탕으로 파란 통화 버튼, Receiver 상태 카드, IP 주소 카드, 기기 다시 찾기 버튼을 구현했다.
송신/수신 모드는 우측 상단 배지를 눌러 변경한다. 통화 중에는 모드 전환을 막는다.
Sender IP 카드를 누르면 직접 IP를 입력할 수 있고 복사 아이콘으로 주소를 복사한다.
기기 다시 찾기는 NSD 검색의 종료 확인 이후 검색을 다시 시작한다.
Receiver도 같은 디자인으로 수신 대기 상태와 로컬 IP를 표시한다.

## 사용 순서

1. 모든 스마트폰을 같은 가정용 Wi-Fi에 연결한다.
2. 방의 스마트폰에서 우측 상단 모드 메뉴로 수신 모드를 선택하고 마이크·알림 권한을 허용한다.
3. Receiver IDLE과 음성통화 대기 중을 확인한다. 대기 중에는 마이크를 녹음하지 않는다.
4. 가족의 스마트폰에서 송신 모드를 선택한다. NSD로 방의 스마트폰을 검색한다.
5. 음성통화 연결을 누르고 처음에는 마이크 권한을 허용한다. 방의 스마트폰은 자동 수신한다.
6. 양방향으로 대화하고 Sender의 통화 종료 버튼 또는 알림 액션으로 종료한다.

## 연결과 오디오

TCP 5051로 CALL / ACCEPT / READY / ACTIVE handshake와 PING 제어를 한다.
음성은 ephemeral UDP 포트에서 16 kHz mono PCM16, 20 ms 프레임으로 양방향 전송한다.
단일 예약을 원자적으로 확보하여 IDLE → CONNECTING → BUSY로 진행하며,
CONNECTING / BUSY 동안 다른 Sender는 `현재 다른 사용자가 통화 중입니다.` 응답을 받는다.
TCP EOF, 6초 수신 timeout, 음성 UDP 6초 무수신 또는 Wi-Fi 변경 시 통화를 정리한다.
오디오 자원 해제 후 예약을 풀고 Receiver를 IDLE로 복구한다.
큐는 3개 프레임(60 ms)으로 제한하며 역순 패킷은 버리고 누락 구간은 무음으로 출력한다.
VOICE_COMMUNICATION, MODE_IN_COMMUNICATION, 스피커폰, transient audio focus를 사용한다.
AEC / NoiseSuppressor / AGC는 지원 기기에서 활성화한다. 효과와 지연은 기기별 실측이 필요하다.
통화 소켓은 Wi-Fi Network와 로컬 IPv4에 바인딩하며 같은 서브넷의 상대만 허용한다.

## 백그라운드

Receiver microphone foreground service는 화면이 보일 때 권한을 받고 준비한다.
이후 화면이 꺼져도 자동 수신하도록 구현했다. Sender도 microphone foreground service로 통화를 유지한다.
서비스 수명에 묶인 갱신형 CPU wake lock, Receiver Wi-Fi/multicast lock을 사용한다.
프로세스가 시스템에 의해 재시작되면 Receiver 화면을 다시 열어 마이크 대기를 활성화해야 한다.
강제 종료/재부팅 이후에도 앱을 다시 열어야 하며 제조사 절전 정책은 실기기에서 확인한다.

## 테스트

- Receiver: 화면을 끄고 자동 수신, BUSY 표시, 종료 후 IDLE과 마이크 해제를 확인한다.
- Sender: 검색, 최초 권한, 동시 발화, 스피커 출력, 종료, Wi-Fi 끊김 후 자동 복구를 확인한다.
- Sender A/B: A 통화 중 B 거절, A 종료 후 B 연결, 동시 요청 시 한 대만 승인되는지 확인한다.
- TV가 켜진 방에서 echo와 소음, 충전 상태 장시간 대기를 별도 검증한다.

2026-10-05 검증: Debug APK 빌드, JVM 단위 테스트 7개 통과.
연결된 Android 기기에서 RoomCallScreenTest 3개 통과: 통화/종료/검색 버튼, 메시지 버튼 제거,
IP 편집, 모드 전환, Receiver 대기 상태. 실제 앱 시작과 NSD 검색/IDLE 표시도 확인했다.
화면 테스트는 통화 엔진을 시작하지 않는다. full-duplex 오디오와 장시간 안정성은 별도 검증 항목이다.

## 이미지

- captures/roomcall-sender-preview.png: 실제 Android Compose 렌더링, IP와 상태는 테스트용 값
- captures/roomcall-receiver-preview.png: 실제 Android Compose 렌더링, IP와 상태는 테스트용 값
- captures/roomcall-main-live.png: 연결된 스마트폰에서 실행 중인 실제 앱 화면

captures 폴더는 Git에 포함하지 않는다.

## 빌드

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot'
$env:GRADLE_USER_HOME = "$PWD\.gradle-agent"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

Gradle connectedDebugAndroidTest 실행에 필요한 UTP 패키지가 오프라인 캐시에 없어,
빌드된 app-debug-androidTest.apk를 직접 설치한 후 AndroidJUnitRunner로 화면 테스트를 실행했다.
APK: app/build/outputs/apk/debug/app-debug.apk

## Manifest

RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, FOREGROUND_SERVICE_MICROPHONE, CHANGE_WIFI_MULTICAST_STATE,
INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_CONNECTED_DEVICE, WAKE_LOCK, POST_NOTIFICATIONS를 사용한다.
Receiver type은 connectedDevice|microphone, Sender type은 microphone이며 둘 다 exported=false다.