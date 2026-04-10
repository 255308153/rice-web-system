# 水稻图像识别推理服务（FastAPI）

该服务用于给后端 `YoloService` 提供在线识别接口，支持：
- `GET /health`：连通性检查
- `POST /predict`：接收 base64 图片，返回品种 + 病害 + 置信度 + 建议

当前版本已支持“真实模型推理”：
- 优先加载 TensorFlow/Keras 模型做推理
- 模型缺失时可按配置回退到启发式兜底（避免服务不可用）

## 1. 安装依赖

```bash
cd ai-inference
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

若要启用真实模型推理，再安装：

```bash
pip install -r requirements-real-model.txt
```

## 2. 准备模型文件

把训练好的模型放到 `ai-inference/models/`：

- `rice_classifier.keras`（米种模型）
- `rice_disease_densenet201.keras`（病害模型）

可通过环境变量改路径：

- `RICE_MODEL_PATH`
- `DISEASE_MODEL_PATH`

## 3. 启动服务

```bash
uvicorn app:app --host 0.0.0.0 --port 8001
```

## 4. 后端配置

在管理端“系统配置 -> AI参数”把 `YOLO 服务地址` 设置为：

```text
http://127.0.0.1:8001
```

后端会自动拼接：
- 识别：`/predict`
- 健康检查：`/health`

## 5. 关键环境变量

- `ENABLE_HEURISTIC_FALLBACK`：是否允许无模型时兜底（默认 `true`）
- `RICE_CLASS_NAMES`：米种标签顺序，逗号分隔
- `DISEASE_CLASS_NAMES`：病害标签顺序，逗号分隔
- `RICE_LABELS_FILE`：米种标签文件路径（默认 `models/rice_labels.txt`）
- `DISEASE_LABELS_FILE`：病害标签文件路径（默认 `models/disease_labels.txt`）
- `AI_PROVIDER`：返回给后端的 provider 标识
- `AI_MODEL_VERSION`：返回给后端的模型版本

默认标签顺序（仅在未提供 labels 文件/环境变量时使用）：
- 米种：`Arborio,Basmati,Ipsala,Jasmine,Karacadag`
- 病害：`blast,blight,tungro`

## 6. 接口示例

### 健康检查

```bash
curl -s http://127.0.0.1:8001/health
```

### 识别

```bash
IMG_BASE64=$(base64 -i /path/to/rice.jpg | tr -d '\n')
curl -s http://127.0.0.1:8001/predict \
  -H 'Content-Type: application/json' \
  -d "{\"imageBase64\":\"$IMG_BASE64\",\"filename\":\"rice.jpg\"}"
```

## 7. 训练输出建议

根据你仓库里的 notebook：

- `rice-dataset.ipynb`：输出米种模型（5 类）
- `leaf-rice-disease-classify-densenet201.ipynb`：输出病害模型（3 类）

建议在 notebook 最后补上保存：

```python
model.save('ai-inference/models/rice_classifier.keras')
```

```python
model.save('ai-inference/models/rice_disease_densenet201.keras')
```

保存后重启服务即可切换到真实模型推理。

## 8. 没有模型时如何快速训练

你现在没有模型，可以直接用脚本训练并导出：

```bash
cd ai-inference
pip install -r requirements.txt
pip install -r requirements-real-model.txt
```

假设你的数据目录结构是：

- 米种：`/path/to/Rice_Image_Dataset/<class_name>/*.jpg`
- 病害：`/path/to/leaf-rice-disease-indonesia/<class_name>/*.jpg`

执行一键训练：

```bash
./train_all.sh /path/to/Rice_Image_Dataset /path/to/leaf-rice-disease-indonesia
```

训练完成后会生成：

- `models/rice_classifier.keras`
- `models/rice_disease_densenet201.keras`

然后重启推理服务，`/health` 显示 `mode=real` 即表示已切到真实模型。
