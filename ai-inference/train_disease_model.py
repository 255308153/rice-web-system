from __future__ import annotations

import argparse
import random
import shutil
from pathlib import Path

import tensorflow as tf


def build_model(num_classes: int, input_size: int, dropout: float = 0.35) -> tuple[tf.keras.Model, tf.keras.Model]:
    base = tf.keras.applications.DenseNet201(
        include_top=False,
        weights="imagenet",
        input_shape=(input_size, input_size, 3),
        pooling="avg",
    )
    base.trainable = False

    inputs = tf.keras.Input(shape=(input_size, input_size, 3))
    x = tf.keras.layers.RandomFlip("horizontal")(inputs)
    x = tf.keras.layers.RandomRotation(0.08)(x)
    x = tf.keras.layers.RandomZoom(0.12)(x)
    x = tf.keras.layers.RandomContrast(0.10)(x)
    x = tf.keras.applications.densenet.preprocess_input(x)
    x = base(x, training=False)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Dropout(dropout)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax")(x)
    return tf.keras.Model(inputs=inputs, outputs=outputs), base


def compile_model(model: tf.keras.Model, learning_rate: float, label_smoothing: float = 0.02) -> None:
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss=tf.keras.losses.CategoricalCrossentropy(label_smoothing=label_smoothing),
        metrics=[
            tf.keras.metrics.CategoricalAccuracy(name="acc"),
            tf.keras.metrics.TopKCategoricalAccuracy(k=2, name="top2"),
        ],
    )


def prepare_subset_dir(data_dir: Path, classes_csv: str | None) -> tuple[Path, Path | None]:
    if not classes_csv:
        return data_dir, None

    classes = [item.strip() for item in classes_csv.split(",") if item.strip()]
    if not classes:
        return data_dir, None

    missing = [name for name in classes if not (data_dir / name).is_dir()]
    if missing:
        raise SystemExit(f"class dir not found under {data_dir}: {missing}")

    subset_dir = data_dir / "__disease_train_subset__"
    if subset_dir.exists():
        shutil.rmtree(subset_dir)
    subset_dir.mkdir(parents=True, exist_ok=True)

    for name in classes:
        source = (data_dir / name).resolve()
        target = subset_dir / name
        target.symlink_to(source, target_is_directory=True)

    return subset_dir, subset_dir


def list_class_images(data_dir: Path, class_names: list[str]) -> list[tuple[str, int]]:
    samples: list[tuple[str, int]] = []
    for label_idx, class_name in enumerate(class_names):
        class_dir = data_dir / class_name
        if not class_dir.is_dir():
            continue
        for path in sorted(class_dir.rglob("*")):
            if path.is_file() and path.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp", ".webp"}:
                samples.append((str(path), label_idx))
    return samples


def stratified_split(
    data_dir: Path,
    class_names: list[str],
    val_ratio: float,
    seed: int,
) -> tuple[list[tuple[str, int]], list[tuple[str, int]]]:
    rng = random.Random(seed)
    train_samples: list[tuple[str, int]] = []
    val_samples: list[tuple[str, int]] = []

    for label_idx, class_name in enumerate(class_names):
        class_dir = data_dir / class_name
        files = [
            str(path)
            for path in sorted(class_dir.rglob("*"))
            if path.is_file() and path.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        ]
        rng.shuffle(files)
        if not files:
            continue
        val_count = max(1, int(len(files) * val_ratio))
        val_files = files[:val_count]
        train_files = files[val_count:]
        if not train_files:
            train_files, val_files = val_files[:-1], val_files[-1:]
        train_samples.extend((fp, label_idx) for fp in train_files)
        val_samples.extend((fp, label_idx) for fp in val_files)

    rng.shuffle(train_samples)
    rng.shuffle(val_samples)
    return train_samples, val_samples


def build_dataset(
    samples: list[tuple[str, int]],
    image_size: int,
    num_classes: int,
    batch_size: int,
    training: bool,
    seed: int,
) -> tf.data.Dataset:
    path_ds = tf.data.Dataset.from_tensor_slices([item[0] for item in samples])
    label_ds = tf.data.Dataset.from_tensor_slices([item[1] for item in samples])
    ds = tf.data.Dataset.zip((path_ds, label_ds))

    def _load(path: tf.Tensor, label_idx: tf.Tensor):
        image_bytes = tf.io.read_file(path)
        image = tf.io.decode_image(image_bytes, channels=3, expand_animations=False)
        image = tf.image.resize(image, (image_size, image_size))
        image = tf.cast(image, tf.float32)
        image = tf.ensure_shape(image, (image_size, image_size, 3))
        label = tf.one_hot(label_idx, depth=num_classes, dtype=tf.float32)
        return image, label

    if training:
        ds = ds.shuffle(buffer_size=max(128, len(samples)), seed=seed, reshuffle_each_iteration=True)
    ds = ds.map(_load, num_parallel_calls=tf.data.AUTOTUNE)
    ds = ds.batch(batch_size)
    ds = ds.prefetch(tf.data.AUTOTUNE)
    return ds


def main() -> None:
    parser = argparse.ArgumentParser(description="Train rice disease classifier and export .keras model")
    parser.add_argument("--data-dir", required=True, help="Directory with class subfolders")
    parser.add_argument("--output", default="models/rice_disease_densenet201.keras", help="Output .keras path")
    parser.add_argument("--val-ratio", type=float, default=0.2, help="Validation ratio for stratified split")
    parser.add_argument("--warmup-epochs", type=int, default=8)
    parser.add_argument("--finetune-epochs", type=int, default=10)
    parser.add_argument("--finetune-at", type=int, default=520, help="DenseNet layer index to start unfreezing")
    parser.add_argument("--warmup-lr", type=float, default=1e-3)
    parser.add_argument("--finetune-lr", type=float, default=1e-5)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--image-size", type=int, default=160)
    parser.add_argument("--classes", default="blast,blight,tungro", help="Comma-separated class folder names")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    data_dir = Path(args.data_dir)
    if not data_dir.exists():
        raise SystemExit(f"data dir not found: {data_dir}")
    train_root, cleanup_dir = prepare_subset_dir(data_dir, args.classes)
    print(f"training root: {train_root}")

    class_names = [item.strip() for item in args.classes.split(",") if item.strip()]
    num_classes = len(class_names)
    if num_classes < 2:
        raise SystemExit("at least 2 classes are required")

    train_samples, val_samples = stratified_split(
        data_dir=train_root,
        class_names=class_names,
        val_ratio=args.val_ratio,
        seed=args.seed,
    )
    if not train_samples or not val_samples:
        raise SystemExit("stratified split failed: train/val samples are empty")

    train_ds = build_dataset(
        samples=train_samples,
        image_size=args.image_size,
        num_classes=num_classes,
        batch_size=args.batch_size,
        training=True,
        seed=args.seed,
    )
    val_ds = build_dataset(
        samples=val_samples,
        image_size=args.image_size,
        num_classes=num_classes,
        batch_size=args.batch_size,
        training=False,
        seed=args.seed,
    )
    print(f"train samples: {len(train_samples)}, val samples: {len(val_samples)}")

    model, base_model = build_model(num_classes=num_classes, input_size=args.image_size)
    compile_model(model=model, learning_rate=args.warmup_lr)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    best_path = output_path.with_name(output_path.stem + ".best.keras")

    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(best_path),
            monitor="val_acc",
            mode="max",
            save_best_only=True,
            save_weights_only=False,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_acc", factor=0.5, patience=2, min_lr=1e-7, verbose=1),
        tf.keras.callbacks.EarlyStopping(monitor="val_acc", patience=5, restore_best_weights=True, verbose=1),
    ]

    warmup_epochs = max(0, args.warmup_epochs)
    finetune_epochs = max(0, args.finetune_epochs)
    total_epochs = warmup_epochs + finetune_epochs

    if warmup_epochs > 0:
        model.fit(train_ds, validation_data=val_ds, epochs=warmup_epochs, callbacks=callbacks, verbose=2)

    if finetune_epochs > 0:
        base_model.trainable = True
        for idx, layer in enumerate(base_model.layers):
            keep_frozen = idx < args.finetune_at
            is_bn = isinstance(layer, tf.keras.layers.BatchNormalization)
            layer.trainable = (not keep_frozen) and (not is_bn)

        compile_model(model=model, learning_rate=args.finetune_lr, label_smoothing=0.0)
        model.fit(
            train_ds,
            validation_data=val_ds,
            initial_epoch=warmup_epochs,
            epochs=total_epochs,
            callbacks=callbacks,
            verbose=2,
        )

    if best_path.exists():
        model = tf.keras.models.load_model(best_path, compile=False)

    model.save(output_path, include_optimizer=False)

    labels_path = output_path.with_name("disease_labels.txt")
    labels_path.write_text("\n".join(class_names) + "\n", encoding="utf-8")

    # Evaluate for reporting (best checkpoint was loaded with compile=False).
    compile_model(model=model, learning_rate=args.finetune_lr, label_smoothing=0.0)
    eval_result = model.evaluate(val_ds, verbose=0, return_dict=True)
    print("validation metrics:", {k: round(float(v), 4) for k, v in eval_result.items()})
    print(f"model saved: {output_path}")
    print(f"labels saved: {labels_path}")
    if best_path.exists():
        print(f"best checkpoint: {best_path}")
    print("class order:", ",".join(class_names))

    if cleanup_dir and cleanup_dir.exists():
        shutil.rmtree(cleanup_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
