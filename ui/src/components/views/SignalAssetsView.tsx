import React from "react";
import { Button, Table, Tag, Upload, message } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import type { UploadProps } from "antd";
import { useSignalAssets } from "../../hooks/useSignalAssets.ts";
import { formatDateTime } from "../../utils";

const SignalAssetsView: React.FC = () => {
  const { assets, loading, uploadAsset } = useSignalAssets();

  const handleUpload: UploadProps["customRequest"] = async (options) => {
    const { file, onSuccess, onError } = options;
    try {
      await uploadAsset(file as File);
      message.success("信号文件上传成功");
      onSuccess?.(file);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "上传失败");
      onError?.(error as Error);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 pb-5">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">数据资产</h1>
            <p className="mt-1 text-sm text-slate-500">
              统一管理 MAT 文件、解析状态和通道识别结果。
            </p>
          </div>
          <Upload
            customRequest={handleUpload}
            showUploadList={false}
            accept=".mat"
          >
            <Button type="primary" icon={<UploadOutlined />}>
              上传 MAT 文件
            </Button>
          </Upload>
        </div>

        <div className="mt-6 rounded-lg border border-slate-200 bg-white">
          <Table
            rowKey="id"
            loading={loading}
            dataSource={assets}
            columns={[
              {
                title: "文件名",
                dataIndex: "filename",
                key: "filename",
                render: (_, record) => (
                  <div>
                    <div className="font-medium text-slate-900">
                      {record.filename}
                    </div>
                    <div className="text-xs text-slate-500">
                      {record.knowledgeBaseName || "系统资产容器"}
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
                title: "信号",
                dataIndex: "signalName",
                key: "signalName",
                width: 180,
                render: (value: string | undefined) => value || "-",
              },
              {
                title: "采样率",
                key: "sampleRate",
                width: 140,
                render: (_, record) =>
                  record.sampleRate ? `${record.sampleRate} Hz` : "-",
              },
              {
                title: "状态",
                key: "processingStatus",
                width: 120,
                render: (_, record) => {
                  const color =
                    record.processingStatus === "READY"
                      ? "green"
                      : record.processingStatus === "FAILED"
                        ? "red"
                        : "gold";
                  return (
                    <Tag color={color}>
                      {record.processingStatus || "PENDING"}
                    </Tag>
                  );
                },
              },
              {
                title: "速度通道",
                key: "hasSpeedSignal",
                width: 120,
                render: (_, record) =>
                  record.hasSpeedSignal ? (
                    <Tag color="blue">已识别</Tag>
                  ) : (
                    <Tag>无</Tag>
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

export default SignalAssetsView;
