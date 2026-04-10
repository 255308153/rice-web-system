from __future__ import annotations

import argparse
from pathlib import Path

import tensorflow as tf


def build_model(num_classes: int, input_size: int) -> tf.keras.Model:
    base = tf.keras.applications.DenseNet201(
        include_top=False,
        weights="imagenet",
        input_shape=(input_size, input_size, 3),
        pooling="avg",
    )
    base.trainable = False

    inputs = tf.keras.Input(shape=(input_size, input_size, 3))
    x = tf.keras.applications.densenet.preprocess_input(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax")(x)
    return tf.keras.Model(inputs=inputs, outputs=outputs)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train rice disease classifier and export .keras model")
    parser.add_argument("--data-dir", required=True, help="Directory with class subfolders")
    parser.add_argument("--output", default="models/rice_disease_densenet201.keras", help="Output .keras path")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--image-size", type=int, default=100)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    data_dir = Path(args.data_dir)
    if not data_dir.exists():
        raise SystemExit(f"data dir not found: {data_dir}")

    train_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        labels="inferred",
        label_mode="categorical",
        validation_split=0.2,
        subset="training",
        seed=args.seed,
        image_size=(args.image_size, args.image_size),
        batch_size=args.batch_size,
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        labels="inferred",
        label_mode="categorical",
        validation_split=0.2,
        subset="validation",
        seed=args.seed,
        image_size=(args.image_size, args.image_size),
        batch_size=args.batch_size,
    )

    class_names = list(train_ds.class_names)
    num_classes = len(class_names)

    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.prefetch(autotune)
    val_ds = val_ds.prefetch(autotune)

    model = build_model(num_classes=num_classes, input_size=args.image_size)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True),
    ]

    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs, callbacks=callbacks)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    model.save(output_path, include_optimizer=False)

    labels_path = output_path.with_name("disease_labels.txt")
    labels_path.write_text("\n".join(class_names) + "\n", encoding="utf-8")

    print(f"model saved: {output_path}")
    print(f"labels saved: {labels_path}")
    print("class order:", ",".join(class_names))


if __name__ == "__main__":
    main()
