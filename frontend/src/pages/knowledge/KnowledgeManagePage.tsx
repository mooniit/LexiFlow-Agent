import {
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { PlusOutlined, UploadOutlined, EyeOutlined, InboxOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import {
  batchImport,
  createKnowledgeBase,
  listChunks,
  listDocuments,
  listKnowledgeBases,
  uploadDocument,
  type DocumentChunk,
  type KnowledgeBase,
  type KnowledgeDocument,
} from '../../api/knowledge';

const { Dragger } = Upload;

export default function KnowledgeManagePage() {
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(true);
  const [createVisible, setCreateVisible] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [chunkVisible, setChunkVisible] = useState(false);
  const [selectedBase, setSelectedBase] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [docLoading, setDocLoading] = useState(false);
  const [chunks, setChunks] = useState<DocumentChunk[]>([]);
  const [chunkLoading, setChunkLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [createForm] = Form.useForm();
  const [uploadForm] = Form.useForm();

  function refreshBases() {
    setLoading(true);
    listKnowledgeBases()
      .then(setBases)
      .catch(() => {})
      .finally(() => setLoading(false));
  }

  useEffect(() => { refreshBases(); }, []);

  async function handleCreate(values: { name: string; visibility: string }) {
    try {
      await createKnowledgeBase(values.name, values.visibility);
      message.success('规则库创建成功');
      setCreateVisible(false);
      createForm.resetFields();
      refreshBases();
    } catch (err: any) {
      message.error(err.message || '创建失败');
    }
  }

  async function handleUpload(values: { title: string; documentType: string }) {
    if (!selectedBase) return;
    const file = uploadForm.getFieldValue('file');
    if (!file) { message.error('请选择文件'); return; }
    setUploading(true);
    try {
      await uploadDocument(selectedBase.id, file, values.title, values.documentType);
      message.success('文档上传成功');
      setUploadVisible(false);
      uploadForm.resetFields();
      loadDocuments(selectedBase.id);
    } catch (err: any) {
      message.error(err.message || '上传失败');
    } finally {
      setUploading(false);
    }
  }

  function loadDocuments(baseId: string) {
    setDocLoading(true);
    listDocuments(baseId)
      .then(setDocuments)
      .catch(() => {})
      .finally(() => setDocLoading(false));
  }

  function openDocs(base: KnowledgeBase) {
    setSelectedBase(base);
    setDocuments([]);
    setUploadVisible(false);
    loadDocuments(base.id);
  }

  function openChunks(docId: string) {
    setChunkVisible(true);
    setChunkLoading(true);
    listChunks(docId)
      .then(setChunks)
      .catch(() => {})
      .finally(() => setChunkLoading(false));
  }

  const baseColumns = [
    { title: 'ID', dataIndex: 'id', width: 60, ellipsis: true },
    { title: '名称', dataIndex: 'name' },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 80,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag>,
    },
    {
      title: '操作',
      width: 280,
      render: (_: unknown, record: KnowledgeBase) => (
        <Space size="small" wrap>
          <Button size="small" icon={<EyeOutlined />} onClick={() => openDocs(record)}>文档</Button>
          <Button size="small" icon={<UploadOutlined />} onClick={() => { setSelectedBase(record); setUploadVisible(true); }}>上传</Button>
          <Button size="small" onClick={() => batchImport(record.id).then(() => { message.success('批量导入成功'); refreshBases(); }).catch((err: any) => message.error(err.message))}>批量导入</Button>
        </Space>
      ),
    },
  ];

  const docColumns = [
    { title: 'ID', dataIndex: 'id', width: 60, ellipsis: true },
    { title: '标题', dataIndex: 'title' },
    { title: '类型', dataIndex: 'documentType', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '状态',
      dataIndex: 'documentStatus',
      width: 80,
      render: (v: string) => <Tag color={v === 'INDEXED' ? 'green' : 'orange'}>{v}</Tag>,
    },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, record: KnowledgeDocument) => (
        <Button size="small" onClick={() => openChunks(record.id)}>切片</Button>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>规则库管理</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateVisible(true)}>
          创建规则库
        </Button>
      </div>

      <Card title={`规则库列表 (${bases.length})`}>
        <Table rowKey="id" dataSource={bases} columns={baseColumns} loading={loading} pagination={false} />
      </Card>

      {selectedBase && (
        <Card title={`${selectedBase.name} — 规则文档`} style={{ marginTop: 16 }}>
          <Table rowKey="id" dataSource={documents} columns={docColumns} loading={docLoading} pagination={false} />
        </Card>
      )}

      {/* Create Knowledge Base Modal */}
      <Modal title="创建规则库" open={createVisible} onCancel={() => setCreateVisible(false)} footer={null}>
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="name" label="规则库名称" rules={[{ required: true }]}>
            <Input placeholder="例：公司合同审查规范" />
          </Form.Item>
          <Form.Item name="visibility" label="可见性" initialValue="PRIVATE">
            <Select options={[
              { label: '私有', value: 'PRIVATE' },
              { label: '公开', value: 'PUBLIC' },
              { label: '部门可见', value: 'DEPARTMENT' },
            ]} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>创建</Button>
        </Form>
      </Modal>

      {/* Upload Document Modal */}
      <Modal title={`上传规则文档 — ${selectedBase?.name || ''}`} open={uploadVisible} onCancel={() => setUploadVisible(false)} footer={null}>
        <Form form={uploadForm} layout="vertical" onFinish={handleUpload}>
          <Form.Item name="file" label="规则文档" rules={[{ required: true }]}>
            <Dragger accept=".txt" maxCount={1} beforeUpload={(f) => { uploadForm.setFieldValue('file', f); return false; }}>
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p>点击或拖拽规则文档上传</p>
              <p style={{ color: '#999' }}>支持 TXT 格式</p>
            </Dragger>
          </Form.Item>
          <Form.Item name="title" label="文档标题" rules={[{ required: true }]}>
            <Input placeholder="例：销售合同审查规范" />
          </Form.Item>
          <Form.Item name="documentType" label="文档类型" initialValue="POLICY">
            <Select options={[
              { label: '审查规范', value: 'POLICY' },
              { label: '标准模板', value: 'TEMPLATE' },
              { label: '法规文件', value: 'REGULATION' },
            ]} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={uploading} block>上传</Button>
        </Form>
      </Modal>

      {/* Chunks Modal */}
      <Modal title="文档切片" open={chunkVisible} onCancel={() => setChunkVisible(false)} width={720} footer={null}>
        {chunkLoading ? <Spin style={{ display: 'block', margin: '20px auto' }} /> : (
          chunks.length === 0 ? <Empty description="暂无切片" /> : (
            chunks.map((c) => (
              <Card key={c.id} size="small" style={{ marginBottom: 8 }} bordered>
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 13, marginBottom: 4 }}>
                  {c.content}
                </Typography.Paragraph>
                <Tag>{`#${c.chunkIndex}`}</Tag>
              </Card>
            ))
          )
        )}
      </Modal>
    </>
  );
}
