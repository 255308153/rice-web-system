#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "Usage: $0 <rice_data_dir> <disease_data_dir> [epochs_rice] [epochs_disease]"
  exit 1
fi

RICE_DATA_DIR="$1"
DISEASE_DATA_DIR="$2"
RICE_EPOCHS="${3:-6}"
DISEASE_EPOCHS="${4:-8}"

cd "$(dirname "$0")"

python3 train_rice_model.py \
  --data-dir "$RICE_DATA_DIR" \
  --output models/rice_classifier.keras \
  --epochs "$RICE_EPOCHS"

python3 train_disease_model.py \
  --data-dir "$DISEASE_DATA_DIR" \
  --output models/rice_disease_densenet201.keras \
  --epochs "$DISEASE_EPOCHS"

echo "done: models are in ai-inference/models/"
