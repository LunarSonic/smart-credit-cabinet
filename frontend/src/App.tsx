import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { ProductsPage } from './pages/ProductsPage';
import { ApplicationsPage } from './pages/ApplicationsPage';
import { ApplicationFormPage } from './pages/ApplicationFormPage';
import { ApplicationDetailPage } from './pages/ApplicationDetailPage';
import { ContractsPage } from './pages/ContractsPage';
import { BankQueuePage } from './pages/BankQueuePage';
import { BankApplicationPage } from './pages/BankApplicationPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/products" replace />} />
        <Route path="products" element={<ProtectedRoute role="CLIENT"><ProductsPage /></ProtectedRoute>} />
        <Route path="applications" element={<ProtectedRoute role="CLIENT"><ApplicationsPage /></ProtectedRoute>} />
        <Route path="applications/new" element={<ProtectedRoute role="CLIENT"><ApplicationFormPage /></ProtectedRoute>} />
        <Route path="applications/:id" element={<ProtectedRoute role="CLIENT"><ApplicationDetailPage /></ProtectedRoute>} />
        <Route path="contracts" element={<ProtectedRoute role="CLIENT"><ContractsPage /></ProtectedRoute>} />
        <Route path="bank/queue" element={<ProtectedRoute role="EMPLOYEE"><BankQueuePage /></ProtectedRoute>} />
        <Route path="bank/applications/:id" element={<ProtectedRoute role="EMPLOYEE"><BankApplicationPage /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
