import React from "react";

interface SidebarProps {
  children: React.ReactNode;
}

const Sidebar: React.FC<SidebarProps> = ({ children }) => {
  return (
    <div
      className="h-full bg-slate-50"
      style={{
        width: "280px",
      }}
    >
      {children}
    </div>
  );
};

export default Sidebar;
