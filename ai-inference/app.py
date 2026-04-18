from __future__ import annotations

import base64
import io
import os
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from PIL import Image

try:
    import tensorflow as tf
except Exception:  # pragma: no cover - runtime optional dependency
    tf = None

app = FastAPI(title="Rice AI Inference Service", version="0.2.0")


def parse_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


SERVICE_PROVIDER = os.getenv("AI_PROVIDER", "tensorflow-hybrid")
MODEL_VERSION = os.getenv("AI_MODEL_VERSION", "model-v1")

ENABLE_HEURISTIC_FALLBACK = parse_bool(os.getenv("ENABLE_HEURISTIC_FALLBACK", "true"), True)
# Disease model can be over-confidently "flat" on OOD images when labels do not include healthy.
# When confidence is lower than this threshold, we run heuristic arbitration to avoid fixed ~0.34 outputs.
DISEASE_LOW_CONF_THRESHOLD = float(os.getenv("DISEASE_LOW_CONF_THRESHOLD", "0.35"))

BASE_DIR = Path(__file__).resolve().parent
DATASET_RAW_DIR = Path(os.getenv("DATASET_RAW_DIR", str(BASE_DIR.parent / "datasets" / "raw")))
CENTROID_MAX_IMAGES_PER_CLASS = int(os.getenv("CENTROID_MAX_IMAGES_PER_CLASS", "0"))
RICE_MODEL_PATH = Path(os.getenv("RICE_MODEL_PATH", str(BASE_DIR / "models" / "rice_classifier.keras")))
DISEASE_MODEL_PATH = Path(os.getenv("DISEASE_MODEL_PATH", str(BASE_DIR / "models" / "rice_disease_densenet201.keras")))
RICE_LABELS_FILE = Path(os.getenv("RICE_LABELS_FILE", str(BASE_DIR / "models" / "rice_labels.txt")))
DISEASE_LABELS_FILE = Path(os.getenv("DISEASE_LABELS_FILE", str(BASE_DIR / "models" / "disease_labels.txt")))


def load_class_names(env_key: str, default_csv: str, labels_file: Path) -> List[str]:
    env_value = os.getenv(env_key)
    if env_value:
        return [part.strip() for part in env_value.split(",") if part.strip()]

    if labels_file.exists():
        lines = [line.strip() for line in labels_file.read_text(encoding="utf-8").splitlines() if line.strip()]
        if lines:
            return lines

    return [part.strip() for part in default_csv.split(",") if part.strip()]


RICE_LABELS = load_class_names(
    env_key="RICE_CLASS_NAMES",
    default_csv="Arborio,Basmati,Ipsala,Jasmine,Karacadag",
    labels_file=RICE_LABELS_FILE,
)

DISEASE_CLASS_NAMES = load_class_names(
    env_key="DISEASE_CLASS_NAMES",
    default_csv="blast,blight,tungro",
    labels_file=DISEASE_LABELS_FILE,
)

DISEASE_LABELS_CN = {
    "blast": "稻瘟病",
    "blight": "白叶枯病",
    "tungro": "东格鲁病",
    "healthy": "未识别病害",
    "normal": "未识别病害",
    "none": "未识别病害",
    "稻瘟病": "稻瘟病",
    "白叶枯病": "白叶枯病",
    "东格鲁病": "东格鲁病",
    "未识别病害": "未识别病害",
}

# Heuristic fallback prototypes (kept for availability when model files are missing).
RICE_PROTOTYPES: Dict[str, np.ndarray] = {
    "Arborio": np.array([0.84, 0.83, 0.76, 0.10, 0.050], dtype=np.float32),
    "Basmati": np.array([0.90, 0.89, 0.84, 0.08, 0.035], dtype=np.float32),
    "Ipsala": np.array([0.88, 0.87, 0.81, 0.09, 0.042], dtype=np.float32),
    "Jasmine": np.array([0.89, 0.88, 0.82, 0.08, 0.038], dtype=np.float32),
    "Karacadag": np.array([0.70, 0.67, 0.58, 0.14, 0.060], dtype=np.float32),
}


FEATURE_KEYS = [
    "mean_r",
    "mean_g",
    "mean_b",
    "std_luma",
    "edge",
    "sat",
    "green_ratio",
    "yellow_ratio",
    "brown_ratio",
    "dark_ratio",
]


class PredictRequest(BaseModel):
    imageBase64: str = Field(..., min_length=20)
    filename: str | None = None


class HealthResponse(BaseModel):
    status: str
    provider: str
    modelVersion: str
    mode: str
    riceModelLoaded: bool
    diseaseModelLoaded: bool
    fallbackEnabled: bool


class PredictResponse(BaseModel):
    type: str
    riceType: str
    diseaseName: str
    riceConfidence: float
    diseaseConfidence: float
    confidence: float
    suggestions: str
    provider: str
    modelVersion: str


class ModelRuntime:
    def __init__(self) -> None:
        self.tf_available = tf is not None
        self.rice_model = None
        self.disease_model = None
        self.rice_model_error: str | None = None
        self.disease_model_error: str | None = None

        if self.tf_available:
            self.rice_model, self.rice_model_error = self._load_model(RICE_MODEL_PATH)
            self.disease_model, self.disease_model_error = self._load_model(DISEASE_MODEL_PATH)

    @staticmethod
    def _load_model(path: Path):
        if not path.exists():
            return None, f"模型文件不存在: {path}"
        try:
            return tf.keras.models.load_model(path, compile=False), None
        except Exception as exc:
            return None, f"模型加载失败({path.name}): {exc}"

    @property
    def rice_loaded(self) -> bool:
        return self.rice_model is not None

    @property
    def disease_loaded(self) -> bool:
        return self.disease_model is not None

    @property
    def mode(self) -> str:
        if self.rice_loaded and self.disease_loaded:
            return "real"
        if self.rice_loaded or self.disease_loaded:
            return "hybrid"
        return "heuristic"

    @property
    def provider(self) -> str:
        if self.mode == "real":
            return SERVICE_PROVIDER or "tensorflow-keras"
        if self.mode == "hybrid":
            return SERVICE_PROVIDER or "tensorflow-hybrid"
        return "heuristic-fallback"

    def health_payload(self) -> Dict[str, object]:
        payload: Dict[str, object] = {
            "status": "ok",
            "provider": self.provider,
            "modelVersion": MODEL_VERSION,
            "mode": self.mode,
            "riceModelLoaded": self.rice_loaded,
            "diseaseModelLoaded": self.disease_loaded,
            "fallbackEnabled": ENABLE_HEURISTIC_FALLBACK,
        }

        if self.rice_model_error:
            payload["riceModelError"] = self.rice_model_error
        if self.disease_model_error:
            payload["diseaseModelError"] = self.disease_model_error
        if not self.tf_available:
            payload["tensorflowError"] = "tensorflow 未安装或导入失败"

        return payload


MODEL_RUNTIME = ModelRuntime()


def decode_base64_bytes(raw_image: str) -> bytes:
    payload = raw_image.strip()
    if payload.startswith("data:image") and "," in payload:
        payload = payload.split(",", 1)[1]

    payload = "".join(payload.split())

    try:
        return base64.b64decode(payload, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"imageBase64 非法: {exc}") from exc

def decode_image_array(image_bytes: bytes) -> np.ndarray:
    if tf is None:
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            arr = np.asarray(image, dtype=np.float32)
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"图片解析失败: {exc}") from exc
        if arr.shape[0] < 8 or arr.shape[1] < 8:
            raise HTTPException(status_code=400, detail="图片尺寸过小，无法识别")
        return arr

    try:
        decoded = tf.io.decode_image(image_bytes, channels=3, expand_animations=False)
        arr = tf.cast(decoded, tf.float32).numpy()
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"图片解析失败: {exc}") from exc

    if arr.ndim != 3 or arr.shape[0] < 8 or arr.shape[1] < 8:
        raise HTTPException(status_code=400, detail="图片尺寸过小，无法识别")
    return arr


def softmax(values: np.ndarray) -> np.ndarray:
    max_value = np.max(values)
    exp = np.exp(values - max_value)
    total = np.sum(exp)
    if total <= 0:
        return np.ones_like(values) / len(values)
    return exp / total


def normalize_probs(values: np.ndarray) -> np.ndarray:
    arr = np.asarray(values, dtype=np.float32).reshape(-1)
    if arr.size == 0:
        raise ValueError("模型输出为空")

    if np.all(arr >= 0):
        total = float(arr.sum())
        if 0.99 <= total <= 1.01:
            return arr
        if total > 0:
            return arr / total
    return softmax(arr)


def get_input_spec(model, default_hw: Tuple[int, int]) -> Tuple[int, int, bool, int]:
    shape = model.input_shape
    if isinstance(shape, list):
        shape = shape[0]

    if not isinstance(shape, tuple) or len(shape) < 4:
        return default_hw[0], default_hw[1], True, 3

    # channels-last: (None, H, W, C)
    if shape[-1] in (1, 3):
        h = int(shape[1] or default_hw[0])
        w = int(shape[2] or default_hw[1])
        c = int(shape[-1])
        return h, w, True, c

    # channels-first: (None, C, H, W)
    if shape[1] in (1, 3):
        h = int(shape[2] or default_hw[0])
        w = int(shape[3] or default_hw[1])
        c = int(shape[1])
        return h, w, False, c

    return default_hw[0], default_hw[1], True, 3


def apply_backbone_preprocess(arr: np.ndarray, preprocess_mode: str | None) -> np.ndarray:
    if tf is None:
        return arr
    mode = str(preprocess_mode or "").strip().lower()
    if not mode:
        return arr
    if mode == "efficientnet":
        return tf.keras.applications.efficientnet.preprocess_input(arr)
    if mode == "densenet":
        return tf.keras.applications.densenet.preprocess_input(arr)
    return arr


def prepare_input(
    image: np.ndarray,
    height: int,
    width: int,
    channels_last: bool,
    channels: int,
    preprocess_mode: str | None = None,
) -> np.ndarray:
    arr = np.asarray(image, dtype=np.float32)
    if arr.ndim != 3:
        raise ValueError("输入图片维度异常，期望 HWC")
    if arr.shape[0] != height or arr.shape[1] != width:
        if tf is not None:
            arr = tf.image.resize(arr, (height, width)).numpy()
        else:
            arr = np.asarray(Image.fromarray(arr.astype(np.uint8)).resize((width, height)), dtype=np.float32)

    if channels == 1:
        arr = np.mean(arr, axis=-1, keepdims=True)
    elif arr.shape[-1] != channels:
        # Best-effort adapt if model channel config differs.
        if channels < arr.shape[-1]:
            arr = arr[..., :channels]
        else:
            pad = np.zeros((arr.shape[0], arr.shape[1], channels - arr.shape[-1]), dtype=np.float32)
            arr = np.concatenate([arr, pad], axis=-1)

    batch = np.expand_dims(arr, axis=0)
    batch = apply_backbone_preprocess(batch, preprocess_mode)
    if channels_last:
        return batch
    return np.transpose(batch, (0, 3, 1, 2))


def predict_with_model(
    model,
    image: np.ndarray,
    class_names: List[str],
    default_hw: Tuple[int, int],
    preprocess_mode: str | None = None,
) -> Tuple[str, float]:
    h, w, channels_last, channels = get_input_spec(model, default_hw)
    inputs = prepare_input(
        image=image,
        height=h,
        width=w,
        channels_last=channels_last,
        channels=channels,
        preprocess_mode=preprocess_mode,
    )
    outputs = model.predict(inputs, verbose=0)

    arr = np.asarray(outputs)
    if arr.ndim == 0:
        raise ValueError("模型输出维度异常")
    if arr.ndim == 1:
        raw = arr
    else:
        raw = arr[0].reshape(-1)

    probs = normalize_probs(raw)
    best_idx = int(np.argmax(probs))

    if 0 <= best_idx < len(class_names):
        label = class_names[best_idx]
    else:
        label = f"class_{best_idx}"

    confidence = float(probs[best_idx])
    return label, confidence


def extract_features(image: Image.Image | np.ndarray) -> Dict[str, float]:
    if isinstance(image, Image.Image):
        arr = np.asarray(image, dtype=np.float32) / 255.0
    else:
        arr = np.asarray(image, dtype=np.float32) / 255.0

    mean_rgb = arr.mean(axis=(0, 1))
    intensity = arr.mean(axis=2)
    sat = arr.max(axis=2) - arr.min(axis=2)

    # Simple texture proxy: average horizontal/vertical gradients.
    grad_x = np.abs(np.diff(intensity, axis=1)).mean()
    grad_y = np.abs(np.diff(intensity, axis=0)).mean()
    edge = float((grad_x + grad_y) / 2.0)

    green_ratio = float(((arr[..., 1] > arr[..., 0] * 1.05) & (arr[..., 1] > arr[..., 2] * 1.05)).mean())
    yellow_ratio = float(((arr[..., 0] > 0.45) & (arr[..., 1] > 0.45) & (arr[..., 2] < 0.42)).mean())
    brown_ratio = float(((arr[..., 0] > 0.35) & (arr[..., 1] > 0.20) & (arr[..., 1] < 0.50) & (arr[..., 2] < 0.35)).mean())
    dark_ratio = float((intensity < 0.20).mean())

    return {
        "mean_r": float(mean_rgb[0]),
        "mean_g": float(mean_rgb[1]),
        "mean_b": float(mean_rgb[2]),
        "std_luma": float(intensity.std()),
        "edge": edge,
        "sat": float(sat.mean()),
        "green_ratio": green_ratio,
        "yellow_ratio": yellow_ratio,
        "brown_ratio": brown_ratio,
        "dark_ratio": dark_ratio,
    }


def _features_to_vector(features: Dict[str, float]) -> np.ndarray:
    return np.array([float(features.get(key, 0.0)) for key in FEATURE_KEYS], dtype=np.float32)


def _iter_image_files(folder: Path) -> List[Path]:
    patterns = ("*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG")
    files: List[Path] = []
    for pattern in patterns:
        files.extend(folder.glob(pattern))
    return files


def build_feature_centroids(class_names: List[str], class_root: Path, max_images: int) -> Dict[str, np.ndarray]:
    centroids: Dict[str, np.ndarray] = {}
    if max_images <= 0 or not class_root.exists():
        return centroids

    for class_name in class_names:
        class_dir = class_root / class_name
        if not class_dir.exists() or not class_dir.is_dir():
            continue

        vectors: List[np.ndarray] = []
        image_files = _iter_image_files(class_dir)
        for img_path in image_files[: max(1, max_images)]:
            try:
                image = Image.open(img_path).convert("RGB")
                vectors.append(_features_to_vector(extract_features(image)))
            except Exception:
                continue

        if vectors:
            centroids[class_name] = np.mean(np.stack(vectors, axis=0), axis=0).astype(np.float32)
    return centroids


def classify_from_centroids(features: Dict[str, float], centroids: Dict[str, np.ndarray]) -> Tuple[str, float]:
    if not centroids:
        raise ValueError("centroids empty")
    vector = _features_to_vector(features)
    labels = list(centroids.keys())
    sims: List[float] = []
    for label in labels:
        distance = float(np.linalg.norm(vector - centroids[label]))
        sims.append(-distance * 6.0)
    probs = softmax(np.array(sims, dtype=np.float32))
    best_index = int(np.argmax(probs))
    return labels[best_index], float(probs[best_index])


RICE_DATASET_CENTROIDS: Dict[str, np.ndarray] | None = None
DISEASE_DATASET_CENTROIDS: Dict[str, np.ndarray] | None = None


def get_rice_dataset_centroids() -> Dict[str, np.ndarray]:
    global RICE_DATASET_CENTROIDS
    if RICE_DATASET_CENTROIDS is None:
        RICE_DATASET_CENTROIDS = build_feature_centroids(
            class_names=RICE_LABELS,
            class_root=DATASET_RAW_DIR / "Rice_Image_Dataset",
            max_images=CENTROID_MAX_IMAGES_PER_CLASS,
        )
    return RICE_DATASET_CENTROIDS


def get_disease_dataset_centroids() -> Dict[str, np.ndarray]:
    global DISEASE_DATASET_CENTROIDS
    if DISEASE_DATASET_CENTROIDS is None:
        DISEASE_DATASET_CENTROIDS = build_feature_centroids(
            class_names=DISEASE_CLASS_NAMES,
            class_root=DATASET_RAW_DIR,
            max_images=CENTROID_MAX_IMAGES_PER_CLASS,
        )
    return DISEASE_DATASET_CENTROIDS


def classify_rice_type(features: Dict[str, float]) -> Tuple[str, float]:
    rice_centroids = get_rice_dataset_centroids()
    if rice_centroids:
        return classify_from_centroids(features, rice_centroids)

    vector = np.array(
        [
            features["mean_r"],
            features["mean_g"],
            features["mean_b"],
            features["std_luma"],
            features["edge"],
        ],
        dtype=np.float32,
    )

    labels = list(RICE_PROTOTYPES.keys())
    sims = []
    for label in labels:
        prototype = RICE_PROTOTYPES[label]
        distance = np.linalg.norm(vector - prototype)
        sims.append(-distance * 8.0)

    probs = softmax(np.array(sims, dtype=np.float32))
    best_index = int(np.argmax(probs))
    return labels[best_index], float(probs[best_index])


def classify_disease(features: Dict[str, float]) -> Tuple[str, float]:
    disease_centroids = get_disease_dataset_centroids()
    if disease_centroids:
        label, confidence = classify_from_centroids(features, disease_centroids)
        return normalize_disease_label(label), confidence

    green_ratio = features["green_ratio"]
    yellow_ratio = features["yellow_ratio"]
    brown_ratio = features["brown_ratio"]
    dark_ratio = features["dark_ratio"]
    sat = features["sat"]
    edge = features["edge"]

    scores = {
        "blast": brown_ratio * 0.55 + dark_ratio * 0.25 + edge * 0.20,
        "blight": yellow_ratio * 0.50 + max(0.0, 0.62 - green_ratio) * 0.30 + sat * 0.20,
        "tungro": yellow_ratio * 0.35 + brown_ratio * 0.25 + sat * 0.20 + max(0.0, 0.58 - green_ratio) * 0.20,
        "healthy": green_ratio * 0.55 + (1 - yellow_ratio) * 0.20 + (1 - brown_ratio) * 0.20 + (1 - dark_ratio) * 0.05,
    }

    labels = ["blast", "blight", "tungro", "healthy"]
    score_values = np.array([scores[label] for label in labels], dtype=np.float32)
    probs = softmax(score_values * 5.0)

    best_index = int(np.argmax(probs))
    best_label = labels[best_index]
    best_confidence = float(probs[best_index])

    # If no disease pattern is dominant, return healthy/unknown.
    if best_label != "healthy" and best_confidence < 0.45 and green_ratio > 0.45:
        best_label = "healthy"
        best_confidence = max(best_confidence, float(probs[labels.index("healthy")]))

    return DISEASE_LABELS_CN[best_label], best_confidence


def normalize_disease_label(label: str) -> str:
    key = str(label or "").strip().lower()
    if not key:
        return "未识别病害"
    return DISEASE_LABELS_CN.get(key, str(label).strip())


def build_suggestions(disease_name: str, rice_type: str) -> str:
    if "稻瘟" in disease_name:
        return "疑似稻瘟病：及时清除病叶，避免偏施氮肥，优先按当地登记药剂进行轮换防治，5-7天复查。"
    if "白叶枯" in disease_name:
        return "疑似白叶枯病：建议清沟排水、控氮稳钾，发病初期按农技建议使用对症药剂，避免串灌。"
    if "东格鲁" in disease_name:
        return "疑似东格鲁病：建议尽快拔除病株并防控虫媒，保持田块通风透光，必要时联系农技站复检。"
    return f"识别品种为{rice_type}，暂未识别出明确病害。建议继续田间巡查，并上传叶片近景图提升识别准确度。"


def predict_real_or_fallback(image: np.ndarray) -> Dict[str, object]:
    features = extract_features(image)

    # Rice type
    if MODEL_RUNTIME.rice_loaded:
        rice_type, rice_confidence = predict_with_model(
            MODEL_RUNTIME.rice_model,
            image,
            class_names=RICE_LABELS,
            default_hw=(50, 50),
            preprocess_mode="efficientnet",
        )
    elif ENABLE_HEURISTIC_FALLBACK:
        rice_type, rice_confidence = classify_rice_type(features)
    else:
        raise HTTPException(status_code=503, detail="米种模型未就绪，且已禁用兜底策略")

    # Disease
    if MODEL_RUNTIME.disease_loaded:
        disease_label, disease_confidence = predict_with_model(
            MODEL_RUNTIME.disease_model,
            image,
            class_names=DISEASE_CLASS_NAMES,
            default_hw=(100, 100),
            # The disease model already contains densenet.preprocess_input in-graph.
            # Avoid double-preprocess here, otherwise prediction may collapse to one class.
            preprocess_mode=None,
        )
        disease_name = normalize_disease_label(disease_label)

        # Low-confidence arbitration: combine model with heuristic fallback to reduce
        # repeated near-uniform probabilities (e.g., around 0.34 for 3 classes).
        if ENABLE_HEURISTIC_FALLBACK and float(disease_confidence) < DISEASE_LOW_CONF_THRESHOLD:
            heuristic_name, heuristic_confidence = classify_disease(features)
            if float(heuristic_confidence) >= float(disease_confidence):
                disease_name = heuristic_name
                disease_confidence = float(heuristic_confidence)
    elif ENABLE_HEURISTIC_FALLBACK:
        disease_name, disease_confidence = classify_disease(features)
    else:
        raise HTTPException(status_code=503, detail="病害模型未就绪，且已禁用兜底策略")

    confidence = max(float(rice_confidence), float(disease_confidence))

    return {
        "type": rice_type,
        "riceType": rice_type,
        "diseaseName": disease_name,
        "riceConfidence": round(float(rice_confidence), 4),
        "diseaseConfidence": round(float(disease_confidence), 4),
        "confidence": round(float(confidence), 4),
        "suggestions": build_suggestions(disease_name, rice_type),
        "provider": MODEL_RUNTIME.provider,
        "modelVersion": MODEL_VERSION,
    }


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    payload = MODEL_RUNTIME.health_payload()
    return HealthResponse(**payload)


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    image_bytes = decode_base64_bytes(req.imageBase64)
    image = decode_image_array(image_bytes)
    result = predict_real_or_fallback(image)
    return PredictResponse(**result)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=8001, reload=False)
