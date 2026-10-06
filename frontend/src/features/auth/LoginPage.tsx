import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { ArrowRight, Boxes, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/app/AuthProvider';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
const schema = z.object({
  email: z.email('Enter a valid email address.').max(254),
  password: z.string().min(1, 'Enter your password.').max(200),
});
type Form = z.infer<typeof schema>;
export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [failure, setFailure] = useState('');
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) });
  if (auth.loading) return <LoadingState />;
  if (auth.user) return <Navigate to="/notifications" replace />;
  async function submit(values: Form) {
    setFailure('');
    try {
      await auth.login(values.email, values.password);
      const from = location.state?.from;
      navigate(
        typeof from === 'string' &&
          from.startsWith('/') &&
          !from.startsWith('//') &&
          !from.startsWith('/login')
          ? from
          : '/notifications',
        { replace: true },
      );
    } catch (e) {
      setFailure(message(e));
    }
  }
  return (
    <div className="login-shell">
      <section className="login-story">
        <a className="brand" href="/">
          <span className="brand-mark">
            <Boxes />
          </span>
          SERMS
        </a>
        <div>
          <p className="eyebrow">SHARED EQUIPMENT. CONNECTED PEOPLE.</p>
          <h1>
            Good work starts
            <br />
            with the right tools.
          </h1>
          <p>
            Keep track of your equipment, reservation updates and return reminders in one shared
            workspace.
          </p>
        </div>
        <p className="login-foot">SWE5006 · Team 07</p>
      </section>
      <main className="login-panel">
        <div className="login-card">
          <span className="login-icon">
            <ShieldCheck size={24} />
          </span>
          <p className="eyebrow">YOUR WORKSPACE</p>
          <h2>Welcome back.</h2>
          <p className="muted">Sign in with your SERMS account to continue.</p>
          {auth.error && (
            <ErrorState message={auth.error.message} onRetry={() => void auth.reload()} />
          )}
          <form onSubmit={handleSubmit(submit)} noValidate>
            <label htmlFor="email">Email address</label>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              placeholder="you@u.nus.edu"
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              {...register('email')}
            />
            {errors.email && (
              <p id="email-error" className="field-error">
                {errors.email.message}
              </p>
            )}
            <label htmlFor="password">Password</label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              {...register('password')}
            />
            {errors.password && (
              <p id="password-error" className="field-error">
                {errors.password.message}
              </p>
            )}
            {failure && (
              <p role="alert" className="field-error">
                {failure}
              </p>
            )}
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Signing in…' : 'Sign in'}
              <ArrowRight size={17} />
            </Button>
          </form>
          <p className="login-help">Need access? Contact your team administrator.</p>
        </div>
      </main>
    </div>
  );
}
