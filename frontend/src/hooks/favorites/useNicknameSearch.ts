import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useUserFavoritesQuery } from '../../api/favorites/hooks/useFavorites';
import { useFavoritesActions } from '../../store/hooks/useFavoritesStore';

export const useNicknameSearch = () => {
  const navigate = useNavigate();
  const { setNickname } = useFavoritesActions();

  const [inputValue, setInputValue] = useState('');
  const [submittedNickname, setSubmittedNickname] = useState<string | null>(null);

  const { data, isFetching, isError, error } = useUserFavoritesQuery(submittedNickname);

  const handleChange = (value: string) => {
    setInputValue(value);
    if (submittedNickname) setSubmittedNickname(null);
  };

  const handleSubmit = () => {
    const trimmed = inputValue.trim();
    if (!trimmed) {
      toast.error('닉네임을 입력해주세요.');
      return;
    }
    setSubmittedNickname(trimmed);
  };

  useEffect(() => {
    if (!data) return;
    setNickname(data.nickname);
    navigate('/tierlist');
  }, [data, navigate, setNickname]);

  return {
    inputValue,
    onChange: handleChange,
    onSubmit: handleSubmit,
    isLoading: isFetching,
    errorMessage: isError ? (error?.detail ?? '사용자를 찾을 수 없습니다.') : null,
  };
};
