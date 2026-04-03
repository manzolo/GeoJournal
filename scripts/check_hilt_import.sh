#!/bin/bash
# Verifica che i file Kotlin modificati usino l'import corretto di hiltViewModel
# Corretto:   androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
# Errato:     androidx.hilt.navigation.compose.hiltViewModel

FILE="$1"

if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  exit 0
fi

# Controlla solo file .kt
if [[ "$FILE" != *.kt ]]; then
  exit 0
fi

if grep -q "hilt.navigation.compose.hiltViewModel" "$FILE"; then
  echo "⚠️  IMPORT ERRATO in $FILE"
  echo "   Trovato:  androidx.hilt.navigation.compose.hiltViewModel"
  echo "   Corretto: androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel"
  exit 1
fi

exit 0
