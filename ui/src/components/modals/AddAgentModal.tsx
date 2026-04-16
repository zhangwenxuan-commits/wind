import React, { useEffect, useState } from "react";
import {
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Select,
  Slider,
} from "antd";
import TextArea from "antd/es/input/TextArea";
import { SaveOutlined } from "@ant-design/icons";
import {
  type AgentVO,
  type ChatOptions,
  type ContextCompressionOptions,
  type CreateAgentRequest,
  getOptionalTools,
  type ModelType,
  type RuntimeCacheOptions,
  type ToolVO,
  type UpdateAgentRequest,
} from "../../api/api.ts";
import { useKnowledgeBases } from "../../hooks/useKnowledgeBases.ts";

interface AddAgentModalProps {
  open: boolean;
  onClose: () => void;
  createAgentHandle: (request: CreateAgentRequest) => Promise<void>;
  updateAgentHandle?: (
    agentId: string,
    request: UpdateAgentRequest,
  ) => Promise<void>;
  editingAgent?: AgentVO | null;
}

const menuItems = [
  { key: "base", label: "基础设置" },
  { key: "model", label: "模型设置" },
  { key: "knowledge", label: "知识库" },
  { key: "tools", label: "工具调用" },
  { key: "runtime", label: "运行时" },
];

function createDefaultCompressionOptions(): ContextCompressionOptions {
  return {
    enabled: false,
    model: undefined,
    minCharsToCompress: 1200,
    maxSummaryChars: 600,
    maxRawPreviewChars: 160,
  };
}

function createDefaultRuntimeCacheOptions(): RuntimeCacheOptions {
  return {
    sessionMemoryEnabled: true,
    sessionMemoryTtlSeconds: 300,
    toolSummaryEnabled: true,
    toolSummaryTtlSeconds: 3600,
  };
}

function createDefaultChatOptions(): ChatOptions {
  return {
    temperature: 0.7,
    topP: 1.0,
    messageLength: 10,
    contextCompression: createDefaultCompressionOptions(),
    runtimeCache: createDefaultRuntimeCacheOptions(),
  };
}

function normalizeChatOptions(chatOptions?: ChatOptions): ChatOptions {
  const defaults = createDefaultChatOptions();
  return {
    ...defaults,
    ...chatOptions,
    contextCompression: {
      ...defaults.contextCompression,
      ...chatOptions?.contextCompression,
    },
    runtimeCache: {
      ...defaults.runtimeCache,
      ...chatOptions?.runtimeCache,
    },
  };
}

function createDefaultFormData(): CreateAgentRequest {
  return {
    name: "agent",
    description: "",
    systemPrompt: "",
    model: "deepseek-chat",
    allowedTools: [],
    allowedKbs: [],
    chatOptions: createDefaultChatOptions(),
  };
}

const AddAgentModal: React.FC<AddAgentModalProps> = ({
  open,
  onClose,
  createAgentHandle,
  updateAgentHandle,
  editingAgent,
}) => {
  const [selectedKey, setSelectedKey] = useState<string>("base");
  const { knowledgeBases } = useKnowledgeBases();
  const [tools, setTools] = useState<ToolVO[]>([]);
  const [formData, setFormData] = useState<CreateAgentRequest>(
    createDefaultFormData(),
  );
  const [createAgentLoading, setCreateAgentLoading] = useState(false);

  useEffect(() => {
    if (editingAgent) {
      setFormData({
        name: editingAgent.name,
        description: editingAgent.description || "",
        systemPrompt: editingAgent.systemPrompt || "",
        model: editingAgent.model,
        allowedTools: editingAgent.allowedTools || [],
        allowedKbs: editingAgent.allowedKbs || [],
        chatOptions: normalizeChatOptions(editingAgent.chatOptions),
      });
    } else {
      setFormData(createDefaultFormData());
    }
    setSelectedKey("base");
  }, [editingAgent, open]);

  useEffect(() => {
    async function fetchTools() {
      try {
        const resp = await getOptionalTools();
        setTools(resp.tools);
      } catch (error) {
        console.error("获取工具列表失败:", error);
      }
    }

    fetchTools().then();
  }, []);

  const isEditMode = !!editingAgent;
  const chatOptions = normalizeChatOptions(formData.chatOptions);
  const compressionOptions = chatOptions.contextCompression!;
  const runtimeCacheOptions = chatOptions.runtimeCache!;

  function updateChatOptions(patch: Partial<ChatOptions>) {
    setFormData((prev) => ({
      ...prev,
      chatOptions: {
        ...normalizeChatOptions(prev.chatOptions),
        ...patch,
      },
    }));
  }

  function updateCompressionOptions(patch: Partial<ContextCompressionOptions>) {
    setFormData((prev) => {
      const current = normalizeChatOptions(prev.chatOptions);
      return {
        ...prev,
        chatOptions: {
          ...current,
          contextCompression: {
            ...current.contextCompression,
            ...patch,
          },
        },
      };
    });
  }

  function updateRuntimeCacheOptions(patch: Partial<RuntimeCacheOptions>) {
    setFormData((prev) => {
      const current = normalizeChatOptions(prev.chatOptions);
      return {
        ...prev,
        chatOptions: {
          ...current,
          runtimeCache: {
            ...current.runtimeCache,
            ...patch,
          },
        },
      };
    });
  }

  function toggleTool(toolName: string, checked: boolean) {
    const currentTools = formData.allowedTools || [];
    setFormData({
      ...formData,
      allowedTools: checked
        ? [...currentTools, toolName]
        : currentTools.filter((item) => item !== toolName),
    });
  }

  function toggleKnowledgeBase(kbId: string, checked: boolean) {
    const currentKbs = formData.allowedKbs || [];
    if (checked) {
      if (currentKbs.includes(kbId) || currentKbs.length >= 10) {
        return;
      }
      setFormData({
        ...formData,
        allowedKbs: [...currentKbs, kbId],
      });
      return;
    }

    setFormData({
      ...formData,
      allowedKbs: currentKbs.filter((item) => item !== kbId),
    });
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={isEditMode ? "编辑 Agent" : "新建 Agent"}
      footer={null}
      width={860}
      centered
    >
      <div className="flex h-[560px]">
        <div className="h-full w-[160px] border-r border-gray-200 pr-2">
          <div className="flex cursor-pointer flex-col gap-0.5 select-none">
            {menuItems.map((item) => {
              const isSelected = selectedKey === item.key;
              return (
                <div
                  key={item.key}
                  onClick={() => setSelectedKey(item.key)}
                  className={`rounded-lg px-3 py-2 hover:bg-gray-100 ${
                    isSelected
                      ? "bg-gray-100 font-medium text-gray-900"
                      : "text-gray-600"
                  }`}
                >
                  {item.label}
                </div>
              );
            })}
          </div>
        </div>
        <div className="relative h-full flex-1">
          <div className="h-full overflow-y-auto px-4 pb-20">
            {selectedKey === "base" && (
              <div className="space-y-4">
                <div>
                  <label className="mb-1 block font-medium text-gray-700">
                    名称
                  </label>
                  <Input
                    placeholder="请输入 Agent 名称"
                    value={formData.name}
                    onChange={(e) =>
                      setFormData({ ...formData, name: e.target.value })
                    }
                  />
                </div>
                <div>
                  <label className="mb-1 block font-medium text-gray-700">
                    描述
                  </label>
                  <TextArea
                    placeholder="简要描述这个 Agent 的职责"
                    rows={3}
                    value={formData.description}
                    onChange={(e) =>
                      setFormData({ ...formData, description: e.target.value })
                    }
                  />
                </div>
                <div>
                  <label className="mb-1 block font-medium text-gray-700">
                    System Prompt
                  </label>
                  <TextArea
                    placeholder="输入该 Agent 的系统提示词"
                    rows={12}
                    value={formData.systemPrompt}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        systemPrompt: e.target.value,
                      })
                    }
                  />
                </div>
              </div>
            )}

            {selectedKey === "model" && (
              <div className="space-y-6">
                <div>
                  <label className="mb-1 block font-medium text-gray-700">
                    主模型
                  </label>
                  <Select
                    options={[
                      { value: "deepseek-chat", label: "deepseek-chat" },
                      { value: "glm-4.6", label: "glm-4.6" },
                    ]}
                    placeholder="请选择主模型"
                    style={{ width: 320 }}
                    value={formData.model}
                    onChange={(value: ModelType) =>
                      setFormData({ ...formData, model: value })
                    }
                  />
                </div>

                <div>
                  <label className="mb-2 block font-medium text-gray-700">
                    模型参数
                  </label>
                  <div className="space-y-5 rounded-xl border border-gray-200 p-4">
                    <div>
                      <div className="mb-2 flex items-center justify-between">
                        <label className="text-sm text-gray-600">
                          Temperature
                        </label>
                        <span className="min-w-[40px] text-right text-sm font-medium text-gray-700">
                          {chatOptions.temperature?.toFixed(1)}
                        </span>
                      </div>
                      <Slider
                        min={0}
                        max={2}
                        step={0.1}
                        value={chatOptions.temperature}
                        onChange={(value) =>
                          updateChatOptions({ temperature: value })
                        }
                      />
                    </div>
                    <div>
                      <div className="mb-2 flex items-center justify-between">
                        <label className="text-sm text-gray-600">Top P</label>
                        <span className="min-w-[40px] text-right text-sm font-medium text-gray-700">
                          {chatOptions.topP?.toFixed(1)}
                        </span>
                      </div>
                      <Slider
                        min={0}
                        max={1}
                        step={0.1}
                        value={chatOptions.topP}
                        onChange={(value) => updateChatOptions({ topP: value })}
                      />
                    </div>
                    <div>
                      <div className="mb-2 flex items-center justify-between">
                        <label className="text-sm text-gray-600">
                          会话窗口长度
                        </label>
                        <span className="min-w-[40px] text-right text-sm font-medium text-gray-700">
                          {chatOptions.messageLength}
                        </span>
                      </div>
                      <Slider
                        min={1}
                        max={100}
                        step={1}
                        value={chatOptions.messageLength}
                        onChange={(value) =>
                          updateChatOptions({ messageLength: value })
                        }
                      />
                    </div>
                  </div>
                </div>
              </div>
            )}

            {selectedKey === "knowledge" && (
              <div className="space-y-4">
                <div>
                  <label className="mb-2 block font-medium text-gray-700">
                    可访问知识库
                  </label>
                  <p className="mb-4 text-sm text-gray-500">
                    最多选择 10 个知识库，Agent 会在这些范围内检索。
                  </p>
                  {knowledgeBases.length === 0 ? (
                    <div className="py-8 text-center text-gray-500">
                      暂无知识库，请先创建知识库。
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {knowledgeBases.map((kb) => {
                        const kbId = kb.knowledgeBaseId;
                        const isSelected = formData.allowedKbs?.includes(kbId);
                        return (
                          <div
                            key={kbId}
                            className={`cursor-pointer rounded-lg border p-4 transition-all hover:border-blue-400 hover:bg-blue-50 ${
                              isSelected
                                ? "border-blue-500 bg-blue-50"
                                : "border-gray-200"
                            }`}
                            onClick={() => toggleKnowledgeBase(kbId, !isSelected)}
                          >
                            <div className="flex items-start gap-3">
                              <Checkbox
                                checked={isSelected}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  toggleKnowledgeBase(kbId, e.target.checked);
                                }}
                              />
                              <div className="flex-1">
                                <div className="mb-1 font-medium text-gray-900">
                                  {kb.name}
                                </div>
                                {kb.description && (
                                  <p className="text-sm text-gray-600">
                                    {kb.description}
                                  </p>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}

            {selectedKey === "tools" && (
              <div className="space-y-4">
                <div>
                  <label className="mb-2 block font-medium text-gray-700">
                    可用工具
                  </label>
                  <p className="mb-4 text-sm text-gray-500">
                    这里控制 Agent 能调用哪些工具。工具结果压缩和缓存请到“运行时”页签配置。
                  </p>
                  {tools.length === 0 ? (
                    <div className="py-8 text-center text-gray-500">
                      暂无可用工具。
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {tools.map((tool) => {
                        const toolId = tool.name;
                        const isSelected = formData.allowedTools?.includes(toolId);
                        return (
                          <div
                            key={toolId}
                            className={`cursor-pointer rounded-lg border p-4 transition-all hover:border-blue-400 hover:bg-blue-50 ${
                              isSelected
                                ? "border-blue-500 bg-blue-50"
                                : "border-gray-200"
                            }`}
                            onClick={() => toggleTool(toolId, !isSelected)}
                          >
                            <div className="flex items-start gap-3">
                              <Checkbox
                                checked={isSelected}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  toggleTool(toolId, e.target.checked);
                                }}
                              />
                              <div className="flex-1">
                                <div className="mb-1 font-medium text-gray-900">
                                  {tool.name}
                                </div>
                                <p className="text-sm text-gray-600">
                                  {tool.description}
                                </p>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}

            {selectedKey === "runtime" && (
              <div className="space-y-6">
                <div className="rounded-xl border border-gray-200 p-4">
                  <div className="mb-3">
                    <div className="mb-1 font-medium text-gray-900">
                      工具结果压缩
                    </div>
                    <p className="text-sm text-gray-500">
                      在工具结果进入下一轮 LLM 上下文前做压缩，适合控制 prompt token。
                    </p>
                  </div>

                  <div className="mb-4">
                    <Checkbox
                      checked={Boolean(compressionOptions.enabled)}
                      onChange={(e) =>
                        updateCompressionOptions({ enabled: e.target.checked })
                      }
                    >
                      启用工具结果压缩
                    </Checkbox>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        压缩模型
                      </label>
                      <Input
                        allowClear
                        placeholder="留空表示跟随主模型，例如 glm-4.6"
                        value={compressionOptions.model || ""}
                        onChange={(e) =>
                          updateCompressionOptions({
                            model: e.target.value.trim() || undefined,
                          })
                        }
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        压缩阈值
                      </label>
                      <InputNumber<number>
                        className="w-full"
                        min={1}
                        max={50000}
                        value={compressionOptions.minCharsToCompress}
                        onChange={(value) =>
                          updateCompressionOptions({
                            minCharsToCompress: value ?? 1200,
                          })
                        }
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        摘要最大长度
                      </label>
                      <InputNumber<number>
                        className="w-full"
                        min={50}
                        max={8000}
                        value={compressionOptions.maxSummaryChars}
                        onChange={(value) =>
                          updateCompressionOptions({
                            maxSummaryChars: value ?? 600,
                          })
                        }
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        原文预览长度
                      </label>
                      <InputNumber<number>
                        className="w-full"
                        min={20}
                        max={4000}
                        value={compressionOptions.maxRawPreviewChars}
                        onChange={(value) =>
                          updateCompressionOptions({
                            maxRawPreviewChars: value ?? 160,
                          })
                        }
                      />
                    </div>
                  </div>

                  <div className="mt-3 rounded-lg bg-gray-50 p-3 text-sm text-gray-600">
                    质量对比建议：先把 Temperature 设为 0，再只切换压缩开关；如果你只想看回复质量，先关闭下面的 tool summary cache。
                  </div>
                </div>

                <div className="rounded-xl border border-gray-200 p-4">
                  <div className="mb-3">
                    <div className="mb-1 font-medium text-gray-900">
                      运行时缓存
                    </div>
                    <p className="text-sm text-gray-500">
                      控制 session memory 和工具摘要缓存，主要影响多轮对话性能。
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="rounded-lg border border-gray-200 p-3">
                      <div className="mb-3">
                        <Checkbox
                          checked={Boolean(runtimeCacheOptions.sessionMemoryEnabled)}
                          onChange={(e) =>
                            updateRuntimeCacheOptions({
                              sessionMemoryEnabled: e.target.checked,
                            })
                          }
                        >
                          启用 session memory cache
                        </Checkbox>
                      </div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        Session TTL（秒）
                      </label>
                      <InputNumber<number>
                        className="w-full"
                        min={10}
                        max={86400}
                        disabled={!runtimeCacheOptions.sessionMemoryEnabled}
                        value={runtimeCacheOptions.sessionMemoryTtlSeconds}
                        onChange={(value) =>
                          updateRuntimeCacheOptions({
                            sessionMemoryTtlSeconds: value ?? 300,
                          })
                        }
                      />
                    </div>

                    <div className="rounded-lg border border-gray-200 p-3">
                      <div className="mb-3">
                        <Checkbox
                          checked={Boolean(runtimeCacheOptions.toolSummaryEnabled)}
                          onChange={(e) =>
                            updateRuntimeCacheOptions({
                              toolSummaryEnabled: e.target.checked,
                            })
                          }
                        >
                          启用 tool summary cache
                        </Checkbox>
                      </div>
                      <label className="mb-1 block text-sm font-medium text-gray-700">
                        Tool summary TTL（秒）
                      </label>
                      <InputNumber<number>
                        className="w-full"
                        min={10}
                        max={86400}
                        disabled={!runtimeCacheOptions.toolSummaryEnabled}
                        value={runtimeCacheOptions.toolSummaryTtlSeconds}
                        onChange={(value) =>
                          updateRuntimeCacheOptions({
                            toolSummaryTtlSeconds: value ?? 3600,
                          })
                        }
                      />
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="absolute bottom-0 right-0 left-0 border-t border-gray-100 bg-white px-4 py-3 text-right">
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={createAgentLoading}
              onClick={async () => {
                setCreateAgentLoading(true);
                try {
                  if (isEditMode && editingAgent && updateAgentHandle) {
                    await updateAgentHandle(editingAgent.id, {
                      ...formData,
                      chatOptions: normalizeChatOptions(formData.chatOptions),
                    });
                  } else {
                    await createAgentHandle({
                      ...formData,
                      chatOptions: normalizeChatOptions(formData.chatOptions),
                    });
                  }
                  onClose();
                } finally {
                  setCreateAgentLoading(false);
                }
              }}
            >
              {isEditMode ? "更新" : "保存"}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default AddAgentModal;
