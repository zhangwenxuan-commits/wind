import React from "react";
import { Button, Table, Tag } from "antd";
import { useNavigate } from "react-router-dom";
import { useDiagnosisTasks } from "../../hooks/useDiagnosisTasks.ts";
import {
  formatDateTime,
  getRiskLevelColor,
  getRiskLevelLabel,
  getTaskStatusColor,
  getTaskStatusLabel,
} from "../../utils";

const DiagnosisTaskListView: React.FC = () => {
  const navigate = useNavigate();
  const { tasks, loading } = useDiagnosisTasks();

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-5">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">诊断任务</h1>
            <p className="mt-1 text-sm text-slate-500">
              任务是诊断流程的一级对象，承载数据、分析结果和结论确认。
            </p>
          </div>
          <Button type="primary" onClick={() => navigate("/tasks/new")}>
            新建任务
          </Button>
        </div>

        <div className="mt-6 rounded-lg border border-slate-200 bg-white">
          <Table
            rowKey="id"
            loading={loading}
            dataSource={tasks}
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
                      {record.summary || "暂无摘要"}
                    </div>
                  </div>
                ),
              },
              {
                title: "设备",
                dataIndex: "deviceName",
                key: "deviceName",
                width: 180,
                render: (value: string | undefined) => value || "-",
              },
              {
                title: "振动文件",
                key: "vibrationAsset",
                width: 220,
                render: (_, record) => record.vibrationAsset?.filename || "-",
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
        </div>
      </div>
    </div>
  );
};

export default DiagnosisTaskListView;
