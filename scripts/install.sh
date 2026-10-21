#!/bin/bash
# Installe ou met à jour Glass sur une Android TV / Fire TV via ADB réseau.
# Usage : scripts/install.sh <IP de la TV> [chemin de l'APK]
# Prérequis : `adb` dans le PATH, débogage ADB activé sur la TV.
set -e
IP="${1:?Usage : scripts/install.sh <IP de la TV> [APK]}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
T="$IP:5555"
adb connect "$T"
sleep 2
adb -s "$T" install -r "$APK"
# Facultatif mais recommandé : recherche Wi-Fi/Bluetooth sans fenêtre de permission,
# fond d'écran / polices perso, et vitesse des animations depuis le Centre de contrôle.
for p in ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION READ_EXTERNAL_STORAGE WRITE_SECURE_SETTINGS; do
  adb -s "$T" shell pm grant com.fares.glass "android.permission.$p" 2>/dev/null || true
done
adb -s "$T" shell am start -n com.fares.glass/.MainActivity
echo "Glass est installé. Choisissez-le comme écran d'accueil (voir README)."
