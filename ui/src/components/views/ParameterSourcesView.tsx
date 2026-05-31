import React from "react";
import { Table } from "antd";
import { useParameterSources } from "../../hooks/useParameterSources.ts";

const ParameterSourcesView: React.FC = () => {
  const { parameterSources, loading } = useParameterSources();

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="border-b border-slate-200 pb-5">
          <h1 className="text-2xl font-semibold text-slate-900">参数与知识</h1>
          <p className="mt-1 text-sm text-slate-500">
            第一阶段先沿用现有知识库作为参数源入口，后续再逐步结构化。
          </p>
        </div>

        <div className="mt-6 rounded-lg border border-slate-200 bg-white">
          <Table
            rowKey="id"
            loading={loading}
            dataSource={parameterSources}
            columns={[
              {
                title: "参数源名称",
                dataIndex: "name",
                key: "name",
                render: (value: string, record) => (
                  <div>
                    <div className="font-medium text-slate-900">{value}</div>
                    <div className="text-xs text-slate-500">
                      {record.description || "暂无描述"}
                    </div>
                  </div>
                ),
              },
              {
                title: "用途",
                key: "purpose",
                width: 240,
                render: () => "任务创建时可作为参数与规则参考源",
              },
            ]}
          />
        </div>
      </div>
    </div>
  );
};

export default ParameterSourcesView;
