export const getAgentEmoji = (agentId: string): string => {
  // 使用 agent id 的哈希值来选择 emoji，确保同一个 agent 总是显示相同的 emoji
  const EMOJI_LIST = [
    "🤖",
    "🎯",
    "🚀",
    "💡",
    "🔮",
    "⚡",
    "🌟",
    "🎨",
    "🔧",
    "📚",
  ];
  let hash = 0;
  for (let i = 0; i < agentId.length; i++) {
    hash = (hash << 5) - hash + agentId.charCodeAt(i);
    hash = hash & hash; // Convert to 32bit integer
  }
  const index = Math.abs(hash) % EMOJI_LIST.length;
  return EMOJI_LIST[index];
};

export const getKnowledgeBaseEmoji = (knowledgeBaseId: string): string => {
  // 知识库相关的 emoji 列表
  const KNOWLEDGE_BASE_EMOJI_LIST = [
    "📚",
    "📖",
    "📝",
    "📋",
    "📑",
    "📄",
    "📃",
    "📊",
    "📈",
    "📉",
  ];
  // 使用知识库 id 的哈希值来选择 emoji，确保同一个知识库总是显示相同的 emoji
  let hash = 0;
  for (let i = 0; i < knowledgeBaseId.length; i++) {
    hash = (hash << 5) - hash + knowledgeBaseId.charCodeAt(i);
    hash = hash & hash; // Convert to 32bit integer
  }
  const index = Math.abs(hash) % KNOWLEDGE_BASE_EMOJI_LIST.length;
  return KNOWLEDGE_BASE_EMOJI_LIST[index];
};

export const formatDateTime = (dateString?: string): string => {
  if (!dateString) return "";
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  
  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60));
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60));
      return minutes <= 0 ? "刚刚" : `${minutes}分钟前`;
    }
    return `${hours}小时前`;
  } else if (days === 1) {
    return "昨天";
  } else if (days < 7) {
    return `${days}天前`;
  } else {
    return date.toLocaleDateString("zh-CN", {
      month: "short",
      day: "numeric",
    });
  }
};

export const getTaskStatusLabel = (status?: string): string => {
  switch (status) {
    case "DRAFT":
      return "草稿";
    case "READY":
      return "待分析";
    case "RUNNING":
      return "分析中";
    case "REVIEW":
      return "待确认";
    case "COMPLETED":
      return "已完成";
    case "FAILED":
      return "失败";
    default:
      return status || "未知";
  }
};

export const getTaskStatusColor = (
  status?: string,
): "default" | "blue" | "gold" | "green" | "red" | "purple" => {
  switch (status) {
    case "READY":
      return "blue";
    case "RUNNING":
      return "gold";
    case "REVIEW":
      return "purple";
    case "COMPLETED":
      return "green";
    case "FAILED":
      return "red";
    default:
      return "default";
  }
};

export const getRiskLevelLabel = (riskLevel?: string): string => {
  switch (riskLevel) {
    case "HIGH":
      return "高风险";
    case "MEDIUM":
      return "中风险";
    case "LOW":
      return "低风险";
    case "UNKNOWN":
      return "待评估";
    default:
      return riskLevel || "待评估";
  }
};

export const getRiskLevelColor = (
  riskLevel?: string,
): "default" | "red" | "gold" | "green" => {
  switch (riskLevel) {
    case "HIGH":
      return "red";
    case "MEDIUM":
      return "gold";
    case "LOW":
      return "green";
    default:
      return "default";
  }
};

interface TaskSummaryLike {
  status?: string;
  riskLevel?: string;
}

export const buildWorkbenchMetrics = (tasks: TaskSummaryLike[]) => {
  return {
    total: tasks.length,
    running: tasks.filter((task) => task.status === "RUNNING").length,
    review: tasks.filter((task) => task.status === "REVIEW").length,
    highRisk: tasks.filter((task) => task.riskLevel === "HIGH").length,
  };
};
