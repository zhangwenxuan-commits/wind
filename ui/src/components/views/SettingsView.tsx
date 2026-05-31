import React from "react";
import { Empty } from "antd";

const SettingsView: React.FC = () => {
  return (
    <div className="flex h-full items-center justify-center bg-slate-100 p-6">
      <div className="w-full max-w-3xl rounded-lg border border-slate-200 bg-white p-10">
        <Empty description="系统配置将在后续阶段承接模型、分析模板和阈值模板管理。" />
      </div>
    </div>
  );
};

export default SettingsView;
