import { Button, Card, Form, Input, InputNumber, message, Select, Typography, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { uploadContract } from '../../api/contract';

const { Dragger } = Upload;

export default function ContractUploadPage() {
  const [loading, setLoading] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const navigate = useNavigate();

  async function handleSubmit(values: Record<string, any>) {
    if (!file) {
      message.error('请选择合同文件');
      return;
    }
    setLoading(true);
    try {
      const contract = await uploadContract(file, {
        contractName: values.contractName,
        contractType: values.contractType,
        contractAmount: values.contractAmount?.toString(),
        customerName: values.customerName,
      });
      message.success(`合同上传成功 #${contract.id}`);
      navigate(`/contracts/${contract.id}`);
    } catch (err: any) {
      message.error(err.message || '上传失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>上传合同</Typography.Title>
      <Card style={{ maxWidth: 720 }}>
        <Form layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="合同文件" required>
            <Dragger
              accept=".txt,.docx"
              maxCount={1}
              beforeUpload={(f) => {
                setFile(f);
                return false;
              }}
              onRemove={() => setFile(null)}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p>点击或拖拽文件上传</p>
              <p style={{ color: '#999' }}>支持 TXT / DOCX 格式</p>
            </Dragger>
          </Form.Item>
          <Form.Item name="contractName" label="合同名称" rules={[{ required: true, message: '请输入合同名称' }]}>
            <Input placeholder="例：2024年度销售合同" />
          </Form.Item>
          <Form.Item name="contractType" label="合同类型" rules={[{ required: true, message: '请选择合同类型' }]}>
            <Select
              placeholder="选择类型"
              options={[
                { label: '销售合同', value: '销售合同' },
                { label: '采购合同', value: '采购合同' },
                { label: '服务合同', value: '服务合同' },
                { label: 'NDA 保密协议', value: 'NDA' },
                { label: '其他', value: '其他' },
              ]}
            />
          </Form.Item>
          <Form.Item name="contractAmount" label="合同金额">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="合同金额（元）" />
          </Form.Item>
          <Form.Item name="customerName" label="客户名称">
            <Input placeholder="对方公司名称" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              提交上传
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </>
  );
}
