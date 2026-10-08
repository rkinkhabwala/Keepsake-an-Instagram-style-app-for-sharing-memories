import { type ChangeEvent, type FormEvent, type ReactNode, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorNotice, Field, inputClass } from '../components/ui';

function AuthShell({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 px-4 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <div className="w-full max-w-sm space-y-6">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.24em] text-emerald-800 dark:text-emerald-300">Keepsake</p>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">{title}</h1>
        </div>
        {children}
        <p className="text-sm text-stone-600 dark:text-stone-400">{footer}</p>
      </div>
    </main>
  );
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await login(identifier, password);
      navigate('/');
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 401
          ? new Error('That username or password is not right.')
          : failure,
      );
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell title="Welcome back" footer={<>New here? <Link to="/signup" className="font-semibold text-emerald-700 dark:text-emerald-400">Create an account</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Username or email">
          <input autoComplete="username" required value={identifier} onChange={(e) => setIdentifier(e.target.value)} className={inputClass} />
        </Field>
        <Field label="Password">
          <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
        </Field>
        <ErrorNotice error={error} />
        <Button type="submit" disabled={pending} className="w-full">
          {pending ? 'Logging in…' : 'Log in'}
        </Button>
      </form>
    </AuthShell>
  );
}

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', displayName: '', password: '' });
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  const update = (field: keyof typeof form) => (event: ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [field]: event.target.value });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    setFieldErrors({});
    try {
      await signup(form);
      navigate('/');
    } catch (failure) {
      setError(failure);
      if (failure instanceof ApiError) setFieldErrors(failure.fieldErrors);
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell title="Start keeping memories" footer={<>Already have an account? <Link to="/login" className="font-semibold text-emerald-700 dark:text-emerald-400">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Username" hint={fieldErrors.username ?? '3–30 characters: lowercase letters, numbers, . and _'}>
          <input
            autoComplete="username"
            required
            pattern="[a-z0-9._]{3,30}"
            value={form.username}
            onChange={(event) => setForm({ ...form, username: event.target.value.toLowerCase() })}
            className={inputClass}
          />
        </Field>
        <Field label="Display name" hint={fieldErrors.displayName}>
          <input autoComplete="name" required maxLength={100} value={form.displayName} onChange={update('displayName')} className={inputClass} />
        </Field>
        <Field label="Email" hint={fieldErrors.email}>
          <input type="email" autoComplete="email" required value={form.email} onChange={update('email')} className={inputClass} />
        </Field>
        <Field label="Password" hint={fieldErrors.password ?? 'At least 8 characters'}>
          <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.password} onChange={update('password')} className={inputClass} />
        </Field>
        <ErrorNotice error={error} />
        <Button type="submit" disabled={pending} className="w-full">
          {pending ? 'Creating account…' : 'Sign up'}
        </Button>
      </form>
    </AuthShell>
  );
}
