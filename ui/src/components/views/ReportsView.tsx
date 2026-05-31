import React from "react";
import { Empty } from "antd";

const ReportsView: React.FC = () => {
  return (
    <div className="flex h-full items-center justify-center bg-slate-100 p-6">
      <div className="w-full max-w-3xl rounded-lg border border-slate-200 bg-white p-10">
        <Empty
          description="报告中心将在下一阶段接入结构化报告与导出能力，本阶段结论先沉淀在任务详情页。"
        />
      </div>
    </div>
  );
};

export default ReportsView;
