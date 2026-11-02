# AMS2 Dash

Dashboard Automobilista 2 pour tablette Android (Galaxy Tab 4, LineageOS 17.1 / Android 10).
L'app écoute directement la télémétrie UDP du jeu : rien à lancer sur le PC.

## Réglages AMS2

*Options › Système* :
- **UDP Protocol Version** : Project CARS 2
- **UDP Frequency** : activée (1 = la plus rapide)

Le jeu diffuse en broadcast sur le port **5606** ; la tablette doit être sur le même réseau local que le PC.

## Build et installation

Prérequis (macOS) : `brew install openjdk@17 && brew install --cask android-commandlinetools`, puis
`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`.

```sh
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Outils de test (`tools/`)

```sh
./replay.py simulate                       # voiture simulée, broadcast sur le réseau local
./replay.py --host 192.168.1.42 simulate   # vers la tablette directement
./record.py session.ams2rec                # enregistre une vraie session AMS2 (Ctrl+C)
./replay.py play session.ams2rec --loop    # rejoue l'enregistrement
```

Avec l'émulateur Android : `adb emu redir add udp:5606:5606` puis `./replay.py --host 127.0.0.1 simulate`.

## Structure

```
android/app/src/main/java/ams2/dash/
  udp/Packets.kt         décodage du protocole (offsets : docs/SMS_UDP_Definitions.hpp)
  udp/Receiver.kt        thread d'écoute UDP
  model/TelemetryState   état courant + conso carburant
  model/LapReference     delta en direct vs meilleur tour valide
  ui/DashboardView       rendu Canvas en repère virtuel 1280×800
  MainActivity           plein écran, écran toujours allumé, verrous Wi-Fi
```

`docs/SMS_UDP_Definitions.hpp`, la définition officielle du protocole UDP Project CARS 2 publiée par
Slightly Mad Studios, sert de référence pour les offsets mais n'est pas versionnée (ce n'est pas notre code) :
la récupérer auprès de SMS / des forums Project CARS 2 et la placer dans `docs/` pour la consulter.
