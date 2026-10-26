import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { useAuth } from '../store/auth';
import { AlfaBankLogoMIcon } from './AlfaIcon';

export function Layout() {
  const auth = useAuth();
  const navigate = useNavigate();
  const cls = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' nav-link-active' : ''}`;

  return (
    <>
      <header className="nav-header">
        <NavLink to={auth.role === 'EMPLOYEE' ? '/bank/queue' : '/products'} className="nav-logo">
          <span className="nav-logo-circle"><AlfaBankLogoMIcon /></span>
          Кредитный кабинет
        </NavLink>
        <nav className="nav-links" aria-label="Основная навигация">
          {auth.role === 'CLIENT' && (<>
            <NavLink to="/products" className={cls}>Продукты</NavLink>
            <NavLink to="/applications" className={cls}>Мои заявки</NavLink>
            <NavLink to="/contracts" className={cls}>Договоры</NavLink>
          </>)}
          {auth.role === 'EMPLOYEE' && <NavLink to="/bank/queue" className={cls}>Заявки</NavLink>}
        </nav>
        <div className="nav-user">
          <span>{auth.fullName}</span>
          <Button size={40} view="secondary" onClick={() => { auth.logout(); navigate('/login'); }}>Выйти</Button>
        </div>
      </header>
      <Outlet />
    </>
  );
}
