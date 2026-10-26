import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Input } from '@alfalab/core-components/input';
import { PasswordInput } from '@alfalab/core-components/password-input';
import { login as loginApi } from '../api';
import { useAuth } from '../store/auth';
import { AlfaBankLIcon } from '../components/AlfaIcon';
import { ErrorPlate } from '../components/Feedback';

const DEMO_PASSWORD = 'Demo1234!';
const DEMO_ACCOUNTS = [
  { login: 'client1', label: 'ИП Петрова: автоодобрение' },
  { login: 'client2', label: 'ИП Волков: иные условия' },
  { login: 'client3', label: 'ООО «Ликвид»: автоотказ' },
  { login: 'client4', label: 'ИП Орлов: ручное рассмотрение' },
  { login: 'client5', label: 'ИП Кузнецова: нужны уточнения' },
  { login: 'client6', label: 'ИП Соколов: БКИ недоступен' },
  { login: 'employee', label: 'Сотрудник банка' },
];

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await loginApi(username, password);
      auth.login(res.token, res.role, res.fullName, res.companyId);
      navigate(res.role === 'EMPLOYEE' ? '/bank/queue' : '/products', { replace: true });
    } catch {
      setError('Неверный логин или пароль');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login">
      <div className="login-brand">
        <div>
          <div className="login-brand-title">
            <AlfaBankLIcon width={56} height={56} />
            <h1>Умный<br />кредитный<br />кабинет</h1>
          </div>
          <p>Кредиты для бизнеса — быстро, прозрачно, без лишних бумаг.</p>
          <ul>
            <li>✓ Решение за 15 минут</li>
            <li>✓ Предодобренные предложения</li>
            <li>✓ Статус заявки онлайн</li>
          </ul>
        </div>
      </div>
      <div className="login-form">
        <form className="login-form-inner" onSubmit={handleSubmit} noValidate>
          <h2 style={{ margin: 0 }}>Войти в кабинет</h2>
          {error && <ErrorPlate>{error}</ErrorPlate>}
          <Input block size={56} label="Логин" autoComplete="username" value={username}
            onChange={(_, { value }) => setUsername(value)} />
          <PasswordInput block size={56} label="Пароль" autoComplete="current-password" value={password}
            onChange={(_, { value }) => setPassword(value)} />
          <Button block size={56} view="accent" type="submit" loading={loading}>Войти</Button>

          <div>
            <p style={{ color: 'var(--color-light-text-secondary)', fontSize: 14 }}>
              Демо-аккаунты: нажмите, чтобы заполнить
            </p>
            <div className="demo-list">
              {DEMO_ACCOUNTS.map((a) => (
                <Button key={a.login} size={32} view="outlined"
                  onClick={() => { setUsername(a.login); setPassword(DEMO_PASSWORD); setError(''); }}>
                  {a.login} · {a.label}
                </Button>
              ))}
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
