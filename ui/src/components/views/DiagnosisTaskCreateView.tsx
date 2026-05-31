import React, { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Select,
  message,
} from "antd";
import { useNavigate } from "react-router-dom";
import { useDiagnosisTasks } from "../../hooks/useDiagnosisTasks.ts";
import { useParameterSources } from "../../hooks/useParameterSources.ts";
import { useSignalAssets } from "../../hooks/useSignalAssets.ts";

const DiagnosisTaskCreateView: React.FC = () => {
  const navigate = useNavigate();
  const { createTask } = useDiagnosisTasks();
  const { assets, loading: assetsLoading } = useSignalAssets();
  const { parameterSources, loading: parameterSourcesLoading } =
    useParameterSources();
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const vibrationOptions = useMemo(
    () =>
      assets.filter(
        (asset) =>
          asset.processingStatus === "READY" &&
          (asset.hasVibrationSignal !== false || asset.documentKind === "VIBRATION_MAT"),
      ),
    [assets],
  );

  const speedOptions = useMemo(
    () =>
      assets.filter(
        (asset) =>
          asset.processingStatus === "READY" && asset.hasSpeedSignal === true,
      ),
    [assets],
  );

  const handleFinish = async (values: {
    title: string;
    deviceName?: string;
    vibrationDocumentId: string;
    speedDocumentId?: string;
    parameterKbId?: string;
    symptomHint?: string;
    referenceShaft?: string;
    envelopeBandHint?: string;
  }) => {
    setSubmitting(true);
    try {
      const taskId = await createTask(values);
      message.success("诊断任务已创建");
      navigate(`/tasks/${taskId}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "创建任务失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-5xl px-6 py-6">
        <div className="border-b border-slate-200 pb-5">
          <h1 className="text-2xl font-semibold text-slate-900">新建诊断任务</h1>
          <p className="mt-1 text-sm text-slate-500">
            先绑定振动信号、转速信号和参数源，再进入任务详情页执行分析。
          </p>
        </div>

        {vibrationOptions.length === 0 ? (
          <div className="mt-6 rounded-lg border border-slate-200 bg-white p-10">
            <Empty
              description="当前没有可用的振动信号资产，请先导入并解析 MAT 文件"
            >
              <Button type="primary" onClick={() => navigate("/assets")}>
                前往数据资产
              </Button>
            </Empty>
          </div>
        ) : (
          <div className="mt-6 rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-200 px-5 py-4">
              <div className="text-base font-medium text-slate-900">
                任务信息
              </div>
              <div className="mt-1 text-sm text-slate-500">
                本阶段任务创建以单振动文件为主，转速和参数源可选。
              </div>
            </div>
            <div className="px-5 py-5">
              <Alert
                type="info"
                showIcon
                className="mb-5"
                message="分析引擎当前直接复用现有振动分析能力，任务流和结构化结果已切换到新的任务模型。"
              />
              <Form
                form={form}
                layout="vertical"
                onFinish={handleFinish}
                initialValues={{
                  referenceShaft: "HSS",
                }}
              >
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Form.Item
                    label="任务名称"
                    name="title"
                    rules={[{ required: true, message: "请输入任务名称" }]}
                  >
                    <Input placeholder="例如：3#机组 HSS 轴承异常复核" />
                  </Form.Item>
                  <Form.Item label="设备/机组" name="deviceName">
                    <Input placeholder="例如：WTG-03 / Gearbox A" />
                  </Form.Item>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Form.Item
                    label="振动信号文件"
                    name="vibrationDocumentId"
                    rules={[{ required: true, message: "请选择振动文件" }]}
                  >
                    <Select
                      loading={assetsLoading}
                      placeholder="选择振动信号"
                      options={vibrationOptions.map((asset) => ({
                        value: asset.id,
                        label: `${asset.filename}${asset.deviceName ? ` / ${asset.deviceName}` : ""}`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item label="转速信号文件" name="speedDocumentId">
                    <Select
                      allowClear
                      loading={assetsLoading}
                      placeholder="可选，选择转速信号"
                      options={speedOptions.map((asset) => ({
                        value: asset.id,
                        label: `${asset.filename}${asset.defaultSpeedSignalName ? ` / ${asset.defaultSpeedSignalName}` : ""}`,
                      }))}
                    />
                  </Form.Item>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Form.Item label="参数源" name="parameterKbId">
                    <Select
                      allowClear
                      loading={parameterSourcesLoading}
                      placeholder="可选，选择参数与知识源"
                      options={parameterSources.map((source) => ({
                        value: source.id,
                        label: source.name,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item label="参考轴" name="referenceShaft">
                    <Select
                      options={[
                        { value: "HSS", label: "HSS" },
                        { value: "MS", label: "MS" },
                      ]}
                    />
                  </Form.Item>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Form.Item label="症状提示" name="symptomHint">
                    <Input placeholder="例如：疑似冲击类故障" />
                  </Form.Item>
                  <Form.Item label="包络频段提示" name="envelopeBandHint">
                    <Input placeholder="例如：8000-10000" />
                  </Form.Item>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button onClick={() => navigate("/tasks")}>取消</Button>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={submitting}
                  >
                    创建任务
                  </Button>
                </div>
              </Form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default DiagnosisTaskCreateView;
