import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Checkbox } from '@alfalab/core-components/checkbox';
import { Input } from '@alfalab/core-components/input';
import { Modal } from '@alfalab/core-components/modal';
import { Select } from '@alfalab/core-components/select';
import { getProducts, createApplication, updateApplication, submitApplication, getApplication } from '../api';
import type { ProductDto, Purpose } from '../api/types';
import { ErrorPlate, PageLoader } from '../components/Feedback';
import { CardTitle, PageTitle } from '../components/InfoGrid';
import { fmt, onlyDigits, purposeOptions } from '../utils';

export function ApplicationFormPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const preapprovedOfferId = params.get('preapprovedOfferId') ?? '';
  const editId = params.get('editId') ?? '';

  const [products, setProducts] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  // id заявки (редактируемой или созданной в этой сессии) и её версия для оптимистичной блокировки
  const [draftId, setDraftId] = useState(editId);
  const [version, setVersion] = useState(0);

  const [productId, setProductId] = useState(params.get('productId') ?? '');
  const [amount, setAmount] = useState('');
  const [termMonths, setTermMonths] = useState('');
  const [purpose, setPurpose] = useState<Purpose>('WORKING_CAPITAL');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [revenue6m, setRevenue6m] = useState('');
  const [monthlyExpenses, setMonthlyExpenses] = useState('');

  const [consentOpen, setConsentOpen] = useState(false);
  const [bkiConsent, setBkiConsent] = useState(false);
  const [pdConsent, setPdConsent] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const prods = await getProducts();
        if (cancelled) return;
        setProducts(prods);
        if (editId) {
          const d = await getApplication(editId);
          if (cancelled) return;
          const s = d.summary;
          // В заявке хранится код продукта, а в списке выбора — id
          setProductId(prods.find((p) => p.code === s.productCode)?.id ?? '');
          setAmount(String(s.amount));
          setTermMonths(String(s.termMonths));
          setPurpose(s.purpose);
          setFullName(d.fullName ?? '');
          setPhone(d.phone ?? '');
          setRevenue6m(d.revenue6m != null ? String(d.revenue6m) : '');
          setMonthlyExpenses(d.monthlyExpenses != null ? String(d.monthlyExpenses) : '');
          setVersion(s.version);
        }
      } catch {
        if (!cancelled) setError('Не удалось загрузить данные.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [editId]);

  const product = products.find((p) => p.id === productId);

  const validate = () => {
    const e: Record<string, string> = {};
    const a = Number(amount), t = Number(termMonths);
    if (!product) e.productId = 'Выберите продукт';
    if (!a) e.amount = 'Введите сумму';
    else if (product && (a < product.minAmount || a > product.maxAmount))
      e.amount = `Сумма должна быть от ${fmt(product.minAmount)} до ${fmt(product.maxAmount)}`;
    if (!t) e.termMonths = 'Введите срок';
    else if (product && (t < product.minTermMonths || t > product.maxTermMonths))
      e.termMonths = `Срок должен быть от ${product.minTermMonths} до ${product.maxTermMonths} мес.`;
    if (revenue6m !== '' && Number(revenue6m) <= 0) e.revenue6m = 'Выручка должна быть больше 0';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  /** Создаёт черновик или обновляет существующий. Возвращает id и новую версию. */
  const persist = async () => {
    const body = {
      amount: Number(amount), termMonths: Number(termMonths), purpose,
      fullName: fullName || undefined, phone: phone || undefined,
      revenue6m: revenue6m ? Number(revenue6m) : undefined,
      monthlyExpenses: monthlyExpenses !== '' ? Number(monthlyExpenses) : undefined,
    };
    if (draftId) {
      const d = await updateApplication(draftId, { ...body, version });
      setVersion(d.summary.version);
      return { id: draftId, version: d.summary.version };
    }
    const created = await createApplication({
      productId: product!.id, amount: body.amount, termMonths: body.termMonths, purpose,
      preapprovedOfferId: preapprovedOfferId || undefined,
    });
    // Контактные поля API принимает только при обновлении — досылаем сразу
    const hasExtra = body.fullName || body.phone || body.revenue6m || body.monthlyExpenses;
    const d = hasExtra ? await updateApplication(created.summary.id, { ...body, version: created.summary.version }) : created;
    setDraftId(d.summary.id);
    setVersion(d.summary.version);
    return { id: d.summary.id, version: d.summary.version };
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setError('');
    try {
      const { id } = await persist();
      navigate(`/applications/${id}`);
    } catch { setError('Ошибка при сохранении заявки. Проверьте данные и попробуйте снова.'); }
    finally { setSaving(false); }
  };

  const handleSubmitClick = async () => {
    if (!validate()) return;
    setSaving(true); setError('');
    try {
      await persist();
      setBkiConsent(false); setPdConsent(false); setConsentOpen(true);
    } catch { setError('Ошибка при сохранении заявки перед отправкой.'); }
    finally { setSaving(false); }
  };

  const handleConsent = async () => {
    if (!bkiConsent || !pdConsent || !draftId) return;
    setSaving(true); setError('');
    try {
      await submitApplication(draftId, { bkiConsent: true, personalDataConsent: true, version });
      setConsentOpen(false);
      navigate(`/applications/${draftId}`);
    } catch { setError('Ошибка при отправке заявки.'); setConsentOpen(false); }
    finally { setSaving(false); }
  };

  if (loading) return <PageLoader />;

  return (
    <div className="page" style={{ maxWidth: 760 }}>
      <div className="page-header"><PageTitle>{editId ? 'Редактировать заявку' : 'Новая заявка'}</PageTitle></div>
      {error && <ErrorPlate>{error}</ErrorPlate>}

      <div className="card stack">
        <CardTitle>Параметры кредита</CardTitle>
        <Select block size={56} label="Кредитный продукт" labelView="outer" placeholder="Выберите продукт"
          options={products.map((p) => ({ key: p.id, content: p.name }))}
          selected={productId || null} disabled={!!editId}
          error={errors.productId}
          onChange={({ selected }) => setProductId(selected?.key ?? '')} />
        <Input block size={56} label="Сумма кредита, ₽" labelView="outer" inputMode="numeric" value={amount}
          placeholder="1000000" error={errors.amount}
          hint={product ? `От ${fmt(product.minAmount)} до ${fmt(product.maxAmount)}` : undefined}
          onChange={(_, { value }) => setAmount(onlyDigits(value))} />
        <Input block size={56} label="Срок, месяцев" labelView="outer" inputMode="numeric" value={termMonths}
          placeholder="12" error={errors.termMonths}
          hint={product ? `От ${product.minTermMonths} до ${product.maxTermMonths} мес.` : undefined}
          onChange={(_, { value }) => setTermMonths(onlyDigits(value))} />
        <Select block size={56} label="Цель кредита" labelView="outer" options={purposeOptions} selected={purpose}
          onChange={({ selected }) => selected && setPurpose(selected.key as Purpose)} />
      </div>

      <div className="card stack">
        <CardTitle>Контактные данные и финансы</CardTitle>
        <Input block size={56} label="ФИО контактного лица" labelView="outer" placeholder="Иванов Иван Иванович"
          value={fullName} onChange={(_, { value }) => setFullName(value)} />
        <Input block size={56} label="Телефон" labelView="outer" type="tel" placeholder="+7 900 000-00-00"
          value={phone} onChange={(_, { value }) => setPhone(value)} />
        <Input block size={56} label="Выручка за 6 месяцев, ₽" labelView="outer" inputMode="numeric" placeholder="3000000"
          value={revenue6m} error={errors.revenue6m} onChange={(_, { value }) => setRevenue6m(onlyDigits(value))} />
        <Input block size={56} label="Ежемесячные расходы, ₽" labelView="outer" inputMode="numeric" placeholder="200000"
          value={monthlyExpenses} onChange={(_, { value }) => setMonthlyExpenses(onlyDigits(value))} />
      </div>

      <div className="row">
        <Button size={56} view="secondary" disabled={saving} onClick={handleSave}>Сохранить черновик</Button>
        <Button size={56} view="accent" loading={saving} onClick={handleSubmitClick}>Отправить на рассмотрение</Button>
      </div>

      <Modal open={consentOpen} onClose={() => setConsentOpen(false)} size={500}>
        <Modal.Header hasCloser title="Согласие на обработку данных" />
        <Modal.Content>
          <p style={{ marginTop: 0 }}>Для отправки заявки нужно ваше согласие на запрос кредитной истории и обработку персональных данных.</p>
          <div className="stack">
            <Checkbox block label="Даю согласие на запрос информации из бюро кредитных историй (БКИ)"
              checked={bkiConsent} onChange={(_, { checked }) => setBkiConsent(checked)} />
            <Checkbox block label="Даю согласие на обработку персональных данных (152-ФЗ)"
              checked={pdConsent} onChange={(_, { checked }) => setPdConsent(checked)} />
          </div>
        </Modal.Content>
        <Modal.Footer>
          <Modal.Controls
            primary={<Button view="accent" loading={saving} disabled={!bkiConsent || !pdConsent} onClick={handleConsent}>Подтвердить и отправить</Button>}
            secondary={<Button view="secondary" onClick={() => setConsentOpen(false)}>Отмена</Button>}
          />
        </Modal.Footer>
      </Modal>
    </div>
  );
}
