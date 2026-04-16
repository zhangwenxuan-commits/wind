import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import AgentChatHistory from "./agentChatView/AgentChatHistory.tsx";
import AgentChatInput from "./agentChatView/AgentChatInput.tsx";
import {
  createChatMessage,
  createChatSession,
  getChatMessagesBySessionId,
  getChatSession,
} from "../../api/api.ts";
import { useAgents } from "../../hooks/useAgents.ts";
import { useChatSessions } from "../../hooks/useChatSessions.ts";
import EmptyAgentChatView from "./agentChatView/EmptyAgentChatView.tsx";
import type { ChatMessageVO, SseMessage, SseMessageType } from "../../types";
import { getAgentEmoji } from "../../utils";

const AgentChatView: React.FC = () => {
  const { chatSessionId } = useParams<{ chatSessionId: string }>();
  const navigate = useNavigate();
  const { state } = useLocation();
  const [loading, setLoading] = useState(false);
  const { agents } = useAgents();
  const { refreshChatSessions } = useChatSessions();

  const [messages, setMessages] = useState<ChatMessageVO[]>([]);
  const [agentId, setAgentId] = useState("");
  const [chatSessionTitle, setChatSessionTitle] = useState("");
  const [displayAgentStatus, setDisplayAgentStatus] = useState(false);
  const [agentStatusText, setAgentStatusText] = useState("");
  const [agentStatusType, setAgentStatusType] = useState<
    SseMessageType | undefined
  >(undefined);

  const addMessage = (message: ChatMessageVO) => {
    setMessages((prevMessages) => [...prevMessages, message]);
  };

  const loadSessionData = useCallback(async () => {
    if (!chatSessionId) {
      return;
    }

    const [messageResp, sessionResp] = await Promise.all([
      getChatMessagesBySessionId(chatSessionId),
      getChatSession(chatSessionId),
    ]);

    setMessages(messageResp.chatMessages);
    setAgentId(sessionResp.chatSession.agentId);
    setChatSessionTitle(sessionResp.chatSession.title || "");
  }, [chatSessionId]);

  useEffect(() => {
    if (!chatSessionId) {
      return;
    }
    loadSessionData().then();
  }, [chatSessionId, loadSessionData]);

  const handleSendMessage = async (value: string | { text: string }) => {
    const message = typeof value === "string" ? value : value.text;
    if (!message || !message.trim()) {
      return;
    }

    if (!chatSessionId) {
      if (!agentId) {
        antdMessage.warning("Select an agent first.");
        return;
      }

      setLoading(true);
      try {
        const response = await createChatSession({
          agentId,
          title: message.slice(0, 20),
        });

        await refreshChatSessions();
        navigate(`/chat/${response.chatSessionId}`, {
          replace: true,
          state: {
            init: false,
            initMessage: message,
          },
        });
      } catch (error) {
        console.error("Failed to create chat session:", error);
        antdMessage.error("Failed to create chat session.");
      } finally {
        setLoading(false);
      }
      return;
    }

    if (state?.init) {
      await createChatMessage({
        agentId: agentId ?? "",
        sessionId: chatSessionId,
        role: "user",
        content: state.initMessage ?? "",
      });
    } else {
      await createChatMessage({
        agentId: agentId ?? "",
        sessionId: chatSessionId,
        role: "user",
        content: message,
      });
    }

    await loadSessionData();
  };

  const currentAgent = useMemo(
    () => agents.find((item) => item.id === agentId),
    [agents, agentId],
  );

  const currentAgentLabel = currentAgent?.name
    ? currentAgent.name
    : agentId
      ? `Unknown agent (${agentId.slice(0, 8)})`
      : "Loading...";

  useEffect(() => {
    if (!chatSessionId) {
      return;
    }

    // TODO: 当前 SSE 重连后只能收到后续新事件。
    // TODO: 后续应在重连成功后补拉历史消息，并恢复该会话最近一次运行状态。
    const es = new EventSource(
      `http://localhost:8080/sse/connect/${chatSessionId}`,
    );

    es.onmessage = (event) => {
      console.log("Received message:", event.data);
    };

    es.onerror = (error) => {
      console.error("SSE error:", error);
    };

    es.addEventListener("message", (event) => {
      const message = JSON.parse(event.data) as SseMessage;
      if (message.type === "AI_GENERATED_CONTENT") {
        addMessage(message.payload.message);
      } else if (message.type === "AI_PLANNING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_PLANNING");
      } else if (message.type === "AI_THINKING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_THINKING");
      } else if (message.type === "AI_EXECUTING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_EXECUTING");
      } else if (message.type === "AI_DONE") {
        setDisplayAgentStatus(false);
        setAgentStatusText("");
        setAgentStatusType(undefined);
      } else {
        throw new Error(`Unknown message type: ${message.type}`);
      }
    });

    es.addEventListener("init", (event) => {
      console.log("Received init message:", event.data);
    });

    return () => {
      console.log("Closing SSE connection.");
      es.close();
    };
  }, [chatSessionId]);

  if (!chatSessionId) {
    return (
      <EmptyAgentChatView
        agents={agents}
        loading={loading}
        handleSendMessage={handleSendMessage}
      />
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-gray-200 bg-white px-6 py-4">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="text-[11px] font-semibold tracking-[0.22em] text-gray-400">
              CURRENT SESSION
            </div>
            <div className="mt-2 flex min-w-0 items-center gap-3">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-yellow-200 via-orange-100 to-red-200 text-xl">
                {currentAgent ? getAgentEmoji(currentAgent.id) : "AI"}
              </div>
              <div className="min-w-0">
                <div className="truncate text-base font-semibold text-gray-900">
                  {chatSessionTitle || "Untitled session"}
                </div>
                <div className="truncate text-sm text-gray-500">
                  Bound agent: {currentAgentLabel}
                </div>
              </div>
            </div>
          </div>
          <div className="shrink-0 rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-500">
            session {chatSessionId.slice(0, 8)}
          </div>
        </div>
      </div>
      <AgentChatHistory
        messages={messages}
        displayAgentStatus={displayAgentStatus}
        agentStatusText={agentStatusText}
        agentStatusType={agentStatusType}
      />
      <div className="border-t border-gray-200 bg-white p-4">
        <AgentChatInput onSend={handleSendMessage} />
      </div>
    </div>
  );
};

export default AgentChatView;
