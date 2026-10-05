# RoomCall LAN 음성 인터폰

기존 메시지 TCP(5050), NSD `RoomCall Receiver` / `_roomcall._tcp.`, 녹음 음성 메시지 재생을 유지한다.
별도 TCP 5051로 통화 제어, ephemeral UDP 포트로 16 kHz / mono / PCM16 양방향 음성을 보낸다.
Firebase, 인터넷 서버, STUN/TURN, 추가 dependency를 사용하지 않는다.
기존 lifecycle dependency가 제공하는 coroutines / StateFlow를 사용한다.

## 연결과 종료

- NSD가 Receiver IP를 찾으면 Sender는 TCP 5051에 상태를 조회한다(화면 표시 중 약 2초 간격).
- CALL 요청의 UDP 포트와 64-bit 임의 세션 토큰을 전달한다.
- Receiver는 단일 예약을 원자적으로 확보하고 IDLE → CONNECTING으로 바꾼다.
- CONNECTING / BUSY 동안 후속 요청은 BUSY 응답을 받고 마이크를 시작하지 않는다.
- ACCEPT / READY / ACTIVE handshake 후 BUSY. 수신 화면 터치 없이 두 기기의 마이크와 스피커가 작동한다.
- 각 기기가 TCP PING을 1초마다 보내며, 6초 수신 timeout 또는 EOF이면 통화를 종료한다.
- 음성 UDP도 6초간 정상 패킷이 없으면 종료한다. Wi-Fi 변경 시 Receiver가 통화를 종료하고 새 주소에 다시 대기한다.
- 오디오 및 네트워크 자원 정리를 마친 뒤 예약을 풀고 Receiver를 IDLE로 복구한다.
- 순서가 뒤집힌 UDP 패킷은 버리고, 수신 큐는 최대 3개(60ms)로 제한한다. 누락 구간은 무음으로 출력한다.
- 미디어 지연은 오디오 하드웨어 버퍼와 Wi-Fi 상태에 따라 달라지며, 실측은 두 기기에서 필요하다.

## Android 정책과 오디오

Receiver 최초 실행 / 수신 모드 전환 시 마이크 runtime 권한을 허용한다. 화면이 보이는 상태에서
connectedDevice + microphone foreground service를 준비하고, 대기 중에는 AudioRecord를 생성하지 않는다.
마이크는 승인된 통화에만 켜지며, Sender에도 microphone foreground service가 있어 화면이 꺼져도 통화가 유지된다.
알림을 누르면 앱 화면을 열 수 있고 Sender 알림의 통화 종료 액션으로 종료할 수도 있다.

프로세스를 시스템이 종료하고 sticky Receiver를 재시작하면 메시지 수신은 재개한다.
백그라운드 마이크 서비스 시작 제한을 우회하지 않으므로, 음성 자동 수신은 Receiver 화면을 다시 열어 활성화해야 한다.
강제 종료 또는 기기 재부팅 뒤에는 앱을 다시 열어야 한다. 충전 상태 장시간 대기를 위해 Receiver는 Wi-Fi/multicast lock,
서비스 수명에 묶인 갱신형 CPU wake lock을 사용한다. 제조사 절전 정책은 실기기에서 확인한다.

VOICE_COMMUNICATION, MODE_IN_COMMUNICATION, 스피커폰, transient audio focus를 사용한다.
AEC / NoiseSuppressor / AGC가 기기에서 지원되면 오디오 세션에 활성화한다. 지원 여부와 TV 소음 제거 효과는 기기별로 다르다.
종료 시 효과, 마이크, 스피커, focus를 해제하고 이전 오디오 모드와 출력 장치를 복구한다.
다른 앱이 audio focus를 가져가면 통화를 종료한다.

통화 소켓은 Wi-Fi Network와 로컬 IP에 바인딩하고 같은 IPv4 서브넷의 상대만 허용한다.
이 구현은 IPv4 가정용 Wi-Fi 기준이다. 게스트 Wi-Fi/AP isolation/VLAN으로 기기간 통신이 차단되면 연결할 수 없다.

## Receiver 테스트

1. 새 Debug APK를 설치하고 같은 가정용 Wi-Fi에 연결한다.
2. 수신 모드를 선택하고 마이크 권한, 알림 권한을 허용한다.
3. IP, 수신 대기 중, 상태 IDLE을 확인한다. 마이크 사용 표시가 대기 중에는 켜지지 않아야 한다.
4. 화면을 끄거나 다른 앱을 연다. Sender에서 연결하면 자동으로 통화가 시작되어야 한다.
5. 화면을 다시 열어 BUSY 표시와 두 방향의 목소리를 확인한다. 스피커 음량을 적절히 설정한다.
6. Sender 종료 후 IDLE과 마이크 사용 표시 해제를 확인한다.
7. 기존 메시지 버튼 3개의 음성 파일 재생과 수신 메시지 표시를 확인한다.
8. Receiver 앱 강제 종료/시스템 재시작 이후 수신 화면을 다시 열어 음성 대기를 활성화한다.

## Sender 테스트

1. Receiver와 같은 Wi-Fi에 연결하고 송신 모드를 선택한다.
2. IP 입력 없이 NSD 검색으로 아이 방 스마트폰 발견 및 Receiver IDLE을 확인한다.
3. 음성통화 연결을 누르고 처음에는 마이크 권한을 허용한다.
4. 통화 중 상태, 양방향 동시 발화, 양쪽 스피커 출력을 확인한다.
5. 화면을 꺼도 통화를 유지하는지 확인한 뒤 통화 종료 버튼 또는 알림 액션으로 종료한다.
6. Sender Wi-Fi를 끄거나 앱을 강제 종료하여 Receiver가 보통 6초 + 자원 정리 시간 내 IDLE이 되는지 확인한다.
7. 연결 중 즉시 종료, 회전, 반복 연결/종료, TV가 켜진 방의 echo/소음도 확인한다.
8. Wi-Fi를 복구하고 재연결한다. NSD가 막힌 공유기에서는 Receiver IP를 직접 입력할 수 있다.

## Sender 2대 이상의 BUSY 테스트

Receiver 1대, Sender A/B 총 3대 이상의 기기가 필요하다.

1. A가 연결되어 Receiver BUSY인 동안 B에서 음성통화 연결을 누른다.
2. B에 `현재 다른 사용자가 통화 중입니다.`가 표시되고 B 마이크는 켜지지 않아야 한다.
3. A의 통화는 유지되어야 한다. A 종료 후 Receiver IDLE을 확인하고 B가 연결할 수 있는지 확인한다.
4. IDLE에서 A/B의 연결 버튼을 동시에 누른다. 한 대만 연결되어야 한다.
5. 선택된 Sender의 Wi-Fi를 끈 뒤 자동 복구 후 다른 Sender가 연결할 수 있는지 확인한다.

## 수정 파일

- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/roomcall/MainActivity.kt
- app/src/main/java/com/example/roomcall/ui/RoomCallScreen.kt
- app/src/main/java/com/example/roomcall/service/RoomCallReceiverService.kt
- app/src/main/java/com/example/roomcall/network/RoomCallNsdDiscovery.kt

## 새 파일

- audio/DuplexAudio.kt
- call/CallState.kt
- call/LanNetwork.kt
- call/CallProtocol.kt
- call/SingleCallerGate.kt
- call/CallSession.kt
- call/CallServer.kt
- call/CallClient.kt
- service/SenderCallService.kt
- service/ServiceWakeLock.kt
- app/src/test/java/com/example/roomcall/call/SingleCallerGateTest.kt
- app/src/test/java/com/example/roomcall/call/CallProtocolTest.kt
- INTERCOM.md

## Manifest 변경

RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, FOREGROUND_SERVICE_MICROPHONE,
CHANGE_WIFI_MULTICAST_STATE를 추가한다. 기존 INTERNET, NETWORK/WIFI, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_CONNECTED_DEVICE, WAKE_LOCK, POST_NOTIFICATIONS는 유지한다.
Receiver service type: connectedDevice|microphone. 새 Sender service type: microphone.
서비스는 모두 exported=false이며 RECORD_AUDIO runtime 요청을 Activity에서 처리한다.

## 빌드 및 검증

Windows PowerShell (정상 JDK 경로를 JAVA_HOME으로 지정):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot'
$env:GRADLE_USER_HOME = "$PWD\.gradle-agent"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

APK: app/build/outputs/apk/debug/app-debug.apk
단위 테스트: 동시 24개 요청의 단일 예약, 소유권 없는 해제 차단, 해제 후 재연결,
프레임 경계, EOF, 크기 제한, UTF-8, 로컬 서브넷 제한.
실제 full-duplex, echo, 장시간 잠금 화면 수신, 다중 스마트폰 BUSY는 실기기 검증 항목이다.
검증 결과(2026-10-05): Debug APK 빌드 성공, 단위 테스트 7개 통과, lint 오류 0개.
Lint 경고는 기존 target SDK/리소스/API 관련 항목과 Intent stopService에 대한 SAM 검사 항목 등이 남아 있다.
두 대 이상 실기기의 양방향 음성·BUSY·잠금 화면 장시간 테스트는 아직 수행하지 않았다.
