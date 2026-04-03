#!/bin/bash
# Verifica parità chiavi tra strings.xml IT e EN
IT="app/src/main/res/values/strings.xml"
EN="app/src/main/res/values-en/strings.xml"

# Estrae le chiavi da ciascun file
keys_it=$(grep -oP '(?<=name=")[^"]+' "$IT" | sort)
keys_en=$(grep -oP '(?<=name=")[^"]+' "$EN" | sort)

missing_en=$(comm -23 <(echo "$keys_it") <(echo "$keys_en"))
missing_it=$(comm -13 <(echo "$keys_it") <(echo "$keys_en"))

ok=true

if [ -n "$missing_en" ]; then
  echo "⚠️  STRINGHE MANCANTI in values-en/strings.xml:"
  echo "$missing_en" | sed 's/^/   - /'
  ok=false
fi

if [ -n "$missing_it" ]; then
  echo "⚠️  STRINGHE MANCANTI in values/strings.xml (IT):"
  echo "$missing_it" | sed 's/^/   - /'
  ok=false
fi

if [ "$ok" = true ]; then
  echo "✅ Parità IT/EN OK ($(echo "$keys_it" | wc -l | tr -d ' ') stringhe)"
fi
