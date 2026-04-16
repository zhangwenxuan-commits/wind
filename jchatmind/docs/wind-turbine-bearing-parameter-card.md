# 风机轴承诊断参数卡 Wind Turbine Bearing Parameter Card

## MAT 文件格式约定 MAT Signal Layout

- 振动信号和转速信号可以放在同一个 MAT 文件里。
- 当前系统支持同一个 MAT 文件同时包含：
  - `AN1` 到 `AN12` 这类振动通道
  - `Speed` 这类转速通道
  - `Torque` 这类扭矩通道
- 如果同一个 MAT 文件同时有振动和转速通道：
  - 振动分析默认优先选择 `AN*`、`CH*`、`acc*`、`vib*`
  - 转速分析默认优先选择 `Speed`、`RPM`、`Tach`
- 如果 MAT 文件里没有显式采样率字段，默认采样率固定为 `40000 Hz`
- 当前默认转速参考轴为 `HSS`

## 推荐包络频段 Recommended Envelope Band

- 推荐包络频段：`8000-10000 Hz`
- 适用场景：风机轴承冲击类故障、早期局部损伤、外圈/内圈/滚动体故障特征增强

## 参考轴 Reference Shaft

- 默认参考轴：`HSS`
- 如果速度信号未额外说明，系统默认将 `Speed` 通道解释为 `HSS` 转速

## 传感器通道映射 Sensor Channel Mapping

| Channel | Description |
| --- | --- |
| AN1 | Main bearing radial |
| AN2 | Main bearing axial |
| AN3 | Ring gear radial 6 o’clock |
| AN4 | Ring gear radial 12 o’clock |
| AN5 | LSS radial |
| AN6 | ISS radial |
| AN7 | HSS radial |
| AN8 | HSS upwind bearing radial |
| AN9 | HSS downwind bearing radial |
| AN10 | Carrier downwind radial |
| AN11 | Generator drive end radial |
| AN12 | Generator non-drive end axial |

## Channel Mapping Notes

- 当前通道映射按你提供的传感器标签表整理
- 如果后续原始测试文档中的表格与这里不一致，应以原始测试标签表为准并同步更新知识库

## Main Bearing INP-A Relative Frequency 主轴承 INP-A 相对频率

参考列：
- `MS` = relative to main shaft
- `HSS` = relative to high speed shaft

| Feature | Relative Freq MS | Relative Freq HSS |
| --- | ---: | ---: |
| roller rotation freq | 5.22 | 0.064 |
| cage freq | 0.45 | 0.006 |
| roller defect freq | 10.44 | 0.128 |
| outer race defect freq | 12.70 | 0.156 |
| inner race defect freq | 15.30 | 0.188 |

## HSS A1 A2 Relative Frequency HSS-A1 and A2 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 3.117 |
| cage freq | 0.425 |
| roller defect freq | 6.234 |
| outer race defect freq | 8.490 |
| inner race defect freq | 11.510 |

## HSS B Relative Frequency HSS-B 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 3.127 |
| cage freq | 0.578 |
| roller defect freq | 6.253 |
| outer race defect freq | 7.174 |
| inner race defect freq | 9.826 |

## ISS C1 C2 Relative Frequency ISS-C1 and C2 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 1.303 |
| cage freq | 0.114 |
| roller defect freq | 2.600 |
| outer race defect freq | 3.525 |
| inner race defect freq | 4.225 |

## ISS D Relative Frequency ISS-D 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.782 |
| cage freq | 0.145 |
| roller defect freq | 1.563 |
| outer race defect freq | 1.793 |
| inner race defect freq | 2.457 |

## LSS E1 E2 Relative Frequency LSS-E1 and E2 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.508 |
| cage freq | 0.033 |
| roller defect freq | 1.017 |
| outer race defect freq | 1.346 |
| inner race defect freq | 1.529 |

## Hollow Shaft F Relative Frequency HS-F 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.579 |
| cage freq | 0.037 |
| roller defect freq | 1.158 |
| outer race defect freq | 1.713 |
| inner race defect freq | 1.933 |

## Carrier G Relative Frequency Carrier-G 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.105 |
| cage freq | 0.006 |
| roller defect freq | 0.211 |
| outer race defect freq | 0.312 |
| inner race defect freq | 0.351 |

## Carrier H Relative Frequency Carrier-H 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.100 |
| cage freq | 0.007 |
| roller defect freq | 0.199 |
| outer race defect freq | 0.294 |
| inner race defect freq | 0.332 |

## Planet 1G 1R Relative Frequency Planet-1G and 1R 轴承相对频率

| Feature | Relative Freq HSS |
| --- | ---: |
| roller rotation freq | 0.102 |
| cage freq | 0.018 |
| roller defect freq | 0.203 |
| outer race defect freq | 0.238 |
| inner race defect freq | 0.300 |

## RAG 检索提示 Retrieval Hints

- 查询 HSS 轴承故障时，优先检索：
  - `HSS A1 A2 Relative Frequency`
  - `HSS B Relative Frequency`
  - `HSS radial`
  - `HSS upwind bearing radial`
  - `HSS downwind bearing radial`
- 查询 ISS 轴承故障时，优先检索：
  - `ISS C1 C2 Relative Frequency`
  - `ISS D Relative Frequency`
  - `ISS radial`
- 查询主轴承故障时，优先检索：
  - `Main Bearing INP-A Relative Frequency`
  - `Main bearing radial`
  - `Main bearing axial`
- 查询包络分析策略时，优先检索：
  - `Recommended Envelope Band`
  - `8000-10000 Hz`
