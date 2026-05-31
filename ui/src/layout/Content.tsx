import React from "react";

interface ContentProps {
  children: React.ReactNode;
}

const Content: React.FC<ContentProps> = ({ children }) => {
  return <div className="h-full flex-1 overflow-hidden bg-slate-100">{children}</div>;
};

export default Content;
