import { Gamepad2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2.5">
      <span className="flex h-[34px] w-[34px] items-center justify-center rounded-xl bg-accent">
        <Gamepad2 className="h-[18px] w-[18px] text-white" aria-hidden="true" />
      </span>
      <span className="text-[15px] font-bold tracking-wide text-text">RBX TIERLIST</span>
    </Link>
  );
}
