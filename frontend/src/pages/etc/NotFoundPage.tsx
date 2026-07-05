import { Link } from 'react-router-dom';
import { PRIMARY_BTN } from '../../constants/common';

export default function NotFoundPage() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 py-24 text-center">
      <p className="text-6xl font-extrabold text-accent">404</p>
      <p className="text-[15px] text-text-sub">페이지를 찾을 수 없어요.</p>
      <Link to="/" className={PRIMARY_BTN}>
        홈으로 돌아가기
      </Link>
    </div>
  );
}
