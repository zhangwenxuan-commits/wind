import React from "react";
import {
  AppstoreOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  RadarChartOutlined,
  SettingOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { useLocation, useNavigate } from "react-router-dom";

interface NavItem {
  key: string;
  label: string;
  path: string;
  icon: React.ReactNode;
}

const navItems: NavItem[] = [
  {
    key: "workbench",
    label: "工作台",
    path: "/workbench",
    icon: <AppstoreOutlined />,
  },
  {
    key: "tasks",
    label: "诊断任务",
    path: "/tasks",
    icon: <UnorderedListOutlined />,
  },
  {
    key: "assets",
    label: "数据资产",
    path: "/assets",
    icon: <RadarChartOutlined />,
  },
  {
    key: "parameters",
    label: "参数与知识",
    path: "/parameters",
    icon: <FolderOpenOutlined />,
  },
  {
    key: "reports",
    label: "报告中心",
    path: "/reports",
    icon: <FileTextOutlined />,
  },
  {
    key: "settings",
    label: "系统配置",
    path: "/settings",
    icon: <SettingOutlined />,
  },
];

const SideMenu: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path: string) => {
    if (path === "/workbench") {
      return location.pathname === "/" || location.pathname.startsWith(path);
    }
    return location.pathname.startsWith(path);
  };

  return (
    <div className="flex h-full flex-col border-r border-slate-200 bg-slate-50">
      <div className="border-b border-slate-200 px-5 py-5">
        <div className="text-lg font-semibold text-slate-900">Winds</div>
        <div className="mt-1 text-sm text-slate-500">风机轴承智能诊断工作台</div>
      </div>
      <div className="flex-1 px-3 py-4">
        <div className="space-y-1">
          {navItems.map((item) => {
            const active = isActive(item.path);
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => navigate(item.path)}
                className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors ${
                  active
                    ? "bg-white font-medium text-slate-900 shadow-sm"
                    : "text-slate-600 hover:bg-white hover:text-slate-900"
                }`}
              >
                <span className="text-base">{item.icon}</span>
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>
      </div>
      <div className="border-t border-slate-200 px-5 py-4 text-xs text-slate-500">
        任务驱动分析与结构化报告
      </div>
    </div>
  );
};

export default SideMenu;
