import { Button } from '@alfalab/core-components/button';
import { Modal } from '@alfalab/core-components/modal';

interface Props {
  open: boolean; title: string; text: string; confirmText: string; danger?: boolean; loading?: boolean;
  onConfirm: () => void; onClose: () => void;
}

export function ConfirmModal({ open, title, text, confirmText, danger, loading, onConfirm, onClose }: Props) {
  return (
    <Modal open={open} onClose={onClose} size={500}>
      <Modal.Header hasCloser title={title} />
      <Modal.Content>{text}</Modal.Content>
      <Modal.Footer>
        <Modal.Controls
          primary={<Button view={danger ? 'accent' : 'primary'} loading={loading} onClick={onConfirm}>{confirmText}</Button>}
          secondary={<Button view="secondary" onClick={onClose}>Отмена</Button>}
        />
      </Modal.Footer>
    </Modal>
  );
}
