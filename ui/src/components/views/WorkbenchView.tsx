import React, { useMemo } from "react";
import { Button, Empty, Statistic, Table, Tag } from "antd";
import { useNavigate } from "react-router-dom";
import { useDiagnosisTasks } from "../../hooks/useDiagnosisTasks.ts";
import { useSignalAssets } from "../../hooks/useSignalAssets.ts";
import {
  buildWorkbenchMetrics,
  formatDateTime,
  getRiskLevelColor,
  getRiskLevelLabel,
  getTaskStatusColor,
  getTaskStatusLabel,
} from "../../utils";

const WorkbenchView: React.FC = () => {
  const navigate = useNavigate();
  const { tasks, loading } = useDiagnosisTasks();
  const { assets } = useSignalAssets();

  const metrics = useMemo(() => buildWorkbenchMetrics(tasks), [tasks]);
  const recentTasks = useMemo(() => tasks.slice(0, 6), [tasks]);

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-5">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">工作台</h1>
            <p className="mt-1 text-sm text-slate-500">
              聚焦待执行任务、分析结果和信号准备状态。
            </p>
          </div>
          <div className="flex gap-2">
            <Button onClick={() => navigate("/assets")}>导入数据</Button>
            <Button type="primary" onClick={() => navigate("/tasks/new")}>
              新建诊断任务
            </Button>
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <Statistic title="任务总数" value={metrics.total} />
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <Statistic title="分析中" value={metrics.running} />
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <Statistic title="待确认" value={metrics.review} />
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <Statistic title="高风险任务" value={metrics.highRisk} />
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-[2fr_1fr]">
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
              <div>
                <div className="text-base font-medium text-slate-900">
                  最近任务
                </div>
                <div className="text-sm text-slate-500">
                  最近更新的诊断任务与当前状态
                </div>
              </div>
              <Button type="link" onClick={() => navigate("/tasks")}>
                查看全部
              </Button>
            </div>
            {recentTasks.length === 0 ? (
              <div className="p-10">
                <Empty description="暂无诊断任务" />
              </div>
            ) : (
              <Table
                rowKey="id"
                loading={loading}
                pagination={false}
                dataSource={recentTasks}
                onRow={(record) => ({
                  onClick: () => navigate(`/tasks/${record.id}`),
                  className: "cursor-pointer",
                })}
                columns={[
                  {
                    title: "任务",
                    dataIndex: "title",
                    key: "title",
                    render: (_, record) => (
                      <div>
                        <div className="font-medium text-slate-900">
                          {record.title}
                        </div>
                        <div className="text-xs text-slate-500">
                          {record.deviceName || "未填写设备"}
                        </div>
                      </div>
                    ),
                  },
                  {
                    title: "状态",
                    key: "status",
                    width: 120,
                    render: (_, record) => (
                      <Tag color={getTaskStatusColor(record.status)}>
                        {getTaskStatusLabel(record.status)}
                      </Tag>
                    ),
                  },
                  {
                    title: "风险",
                    key: "riskLevel",
                    width: 120,
                    render: (_, record) => (
                      <Tag color={getRiskLevelColor(record.riskLevel)}>
                        {getRiskLevelLabel(record.riskLevel)}
                      </Tag>
                    ),
                  },
                  {
                    title: "更新时间",
                    key: "updatedAt",
                    width: 140,
                    render: (_, record) => formatDateTime(record.updatedAt),
                  },
                ]}
              />
            )}
          </div>

          <div className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-white p-5">
              <div className="text-base font-medium text-slate-900">
                数据准备
              </div>
              <div className="mt-1 text-sm text-slate-500">
                当前可用信号资产与任务创建入口
              </div>
              <div className="mt-5 text-3xl font-semibold text-slate-900">
                {assets.length}
              </div>
              <div className="mt-1 text-sm text-slate-500">已导入 MAT 文件</div>
              <Button className="mt-4" block onClick={() => navigate("/assets")}>
                查看数据资产
              </Button>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-5">
              <div className="text-base font-medium text-slate-900">
                快捷入口
              </div>
              <div className="mt-4 space-y-2">
                <Button
                  type="primary"
                  block
                  onClick={() => navigate("/tasks/new")}
                >
                  新建诊断任务
                </Button>
                <Button block onClick={() => navigate("/parameters")}>
                  查看参数与知识
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WorkbenchView;
