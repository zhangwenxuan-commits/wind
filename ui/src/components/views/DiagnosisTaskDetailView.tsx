import React, { useState } from "react";
import {
  Button,
  Descriptions,
  Empty,
  List,
  Spin,
  Table,
  Tag,
  message,
} from "antd";
import { useParams } from "react-router-dom";
import { useDiagnosisTasks } from "../../hooks/useDiagnosisTasks.ts";
import {
  formatDateTime,
  getRiskLevelColor,
  getRiskLevelLabel,
  getTaskStatusColor,
  getTaskStatusLabel,
} from "../../utils";

const DiagnosisTaskDetailView: React.FC = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const { task, loading, startTask, confirmTask } = useDiagnosisTasks(taskId);
  const [submitting, setSubmitting] = useState(false);

  const handleStart = async () => {
    if (!taskId) return;
    setSubmitting(true);
    try {
      await startTask(taskId);
      message.success("分析已完成，结果已写入任务详情");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "执行分析失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirm = async () => {
    if (!taskId) return;
    setSubmitting(true);
    try {
      await confirmTask(taskId, "reviewer");
      message.success("诊断结论已确认");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "确认失败");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading && !task) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-100">
        <Spin />
      </div>
    );
  }

  if (!task) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-100">
        <Empty description="任务不存在" />
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 pb-5">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold text-slate-900">
                {task.title}
              </h1>
              <Tag color={getTaskStatusColor(task.status)}>
                {getTaskStatusLabel(task.status)}
              </Tag>
              <Tag color={getRiskLevelColor(task.riskLevel)}>
                {getRiskLevelLabel(task.riskLevel)}
              </Tag>
            </div>
            <p className="mt-2 text-sm text-slate-500">
              {task.summary || "任务已创建，等待执行分析。"}
            </p>
          </div>
          <div className="flex gap-2">
            <Button loading={submitting} onClick={handleStart}>
              {task.latestAnalysis ? "重新分析" : "执行分析"}
            </Button>
            <Button
              type="primary"
              loading={submitting}
              disabled={!task.latestAnalysis || task.confirmed === true}
              onClick={handleConfirm}
            >
              {task.confirmed ? "已确认" : "确认结论"}
            </Button>
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-[2fr_1fr]">
          <div className="space-y-6">
            <section className="rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-5 py-4 text-base font-medium text-slate-900">
                任务概览
              </div>
              <div className="px-5 py-4">
                <Descriptions column={2} size="small">
                  <Descriptions.Item label="设备/机组">
                    {task.deviceName || "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label="参数源">
                    {task.parameterSource?.name || "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label="参考轴">
                    {task.referenceShaft || "HSS"}
                  </Descriptions.Item>
                  <Descriptions.Item label="症状提示">
                    {task.symptomHint || "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label="包络频段">
                    {task.envelopeBandHint || "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label="更新时间">
                    {formatDateTime(task.updatedAt)}
                  </Descriptions.Item>
                </Descriptions>
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-5 py-4 text-base font-medium text-slate-900">
                关联信号
              </div>
              <Table
                rowKey={(record) => record.key}
                pagination={false}
                dataSource={[
                  {
                    key: "vibration",
                    role: "振动信号",
                    filename: task.vibrationAsset?.filename || "-",
                    deviceName: task.vibrationAsset?.deviceName || "-",
                    sampleRate: task.vibrationAsset?.sampleRate
                      ? `${task.vibrationAsset.sampleRate} Hz`
                      : "-",
                  },
                  {
                    key: "speed",
                    role: "转速信号",
                    filename: task.speedAsset?.filename || "-",
                    deviceName: task.speedAsset?.deviceName || "-",
                    sampleRate: task.speedAsset?.sampleRate
                      ? `${task.speedAsset.sampleRate} Hz`
                      : "-",
                  },
                ]}
                columns={[
                  { title: "角色", dataIndex: "role", key: "role", width: 120 },
                  { title: "文件名", dataIndex: "filename", key: "filename" },
                  { title: "设备", dataIndex: "deviceName", key: "deviceName" },
                  {
                    title: "采样率",
                    dataIndex: "sampleRate",
                    key: "sampleRate",
                    width: 160,
                  },
                ]}
              />
            </section>

            <section className="rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-5 py-4 text-base font-medium text-slate-900">
                分析结果
              </div>
              {!task.latestAnalysis ? (
                <div className="p-10">
                  <Empty description="尚未执行分析" />
                </div>
              ) : (
                <div className="space-y-6 px-5 py-4">
                  <Descriptions column={3} size="small">
                    <Descriptions.Item label="峰值因子">
                      {task.latestAnalysis.basicStats?.crestFactor?.toFixed(2) ||
                        "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="峭度">
                      {task.latestAnalysis.basicStats?.kurtosis?.toFixed(2) ||
                        "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="等效转频">
                      {task.latestAnalysis.speedSummary?.equivalentFrequencyHz?.toFixed(
                        2,
                      ) || "-"}
                    </Descriptions.Item>
                  </Descriptions>

                  <div>
                    <div className="mb-3 text-sm font-medium text-slate-900">
                      主导峰值
                    </div>
                    <Table
                      rowKey={(_, index) => `${index}`}
                      pagination={false}
                      size="small"
                      dataSource={task.latestAnalysis.dominantPeaks || []}
                      columns={[
                        {
                          title: "频率 (Hz)",
                          dataIndex: "frequencyHz",
                          key: "frequencyHz",
                          render: (value: number) => value.toFixed(2),
                        },
                        {
                          title: "幅值",
                          dataIndex: "amplitude",
                          key: "amplitude",
                          render: (value: number) => value.toFixed(4),
                        },
                      ]}
                    />
                  </div>
                </div>
              )}
            </section>
          </div>

          <div className="space-y-6">
            <section className="rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-5 py-4 text-base font-medium text-slate-900">
                诊断结论
              </div>
              <div className="space-y-4 px-5 py-4">
                <div>
                  <div className="text-sm font-medium text-slate-900">结论摘要</div>
                  <div className="mt-1 text-sm leading-6 text-slate-600">
                    {task.latestAnalysis?.conclusion || task.summary || "-"}
                  </div>
                </div>
                <div>
                  <div className="text-sm font-medium text-slate-900">建议动作</div>
                  <div className="mt-1 text-sm leading-6 text-slate-600">
                    {task.latestAnalysis?.recommendation || "-"}
                  </div>
                </div>
                <div>
                  <div className="text-sm font-medium text-slate-900">确认状态</div>
                  <div className="mt-1 text-sm text-slate-600">
                    {task.confirmed
                      ? `${task.confirmedBy || "reviewer"} 已于 ${formatDateTime(task.confirmedAt)} 确认`
                      : "尚未确认"}
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 px-5 py-4 text-base font-medium text-slate-900">
                证据摘要
              </div>
              <div className="px-5 py-4">
                {task.latestAnalysis?.evidence &&
                task.latestAnalysis.evidence.length > 0 ? (
                  <List
                    size="small"
                    dataSource={task.latestAnalysis.evidence}
                    renderItem={(item) => <List.Item>{item}</List.Item>}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无证据" />
                )}
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DiagnosisTaskDetailView;
