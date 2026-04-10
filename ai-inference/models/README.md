# 模型文件目录

将训练好的模型文件放在此目录，默认文件名如下：

- `rice_classifier.keras`：大米品种分类模型
- `rice_disease_densenet201.keras`：水稻病害分类模型

也可通过环境变量覆盖路径：

- `RICE_MODEL_PATH`
- `DISEASE_MODEL_PATH`

默认标签顺序（应与训练时一致）：

- 米种：`Arborio,Basmati,Ipsala,Jasmine,Karacadag`
- 病害：`blast,blight,tungro`

若标签顺序不同，可通过环境变量覆盖：

- `RICE_CLASS_NAMES`
- `DISEASE_CLASS_NAMES`

训练脚本会自动生成：

- `rice_labels.txt`
- `disease_labels.txt`

推理服务会优先读取这两个文件，避免标签顺序错位。
