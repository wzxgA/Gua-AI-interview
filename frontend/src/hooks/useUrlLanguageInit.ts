import { useEffect } from 'react';
import { useLanguage } from '@/contexts/LanguageContext';
import { isSupportedLanguage } from '@/i18n';

/** 候选端语言初始化：优先链接参数 ?lang=xx（仅首次生效），否则沿用全局语言 */
export function useUrlLanguageInit() {
  const { language, setLanguage } = useLanguage();

  useEffect(() => {
    const lang = new URLSearchParams(window.location.search).get('lang');
    if (lang && isSupportedLanguage(lang) && lang !== language) {
      setLanguage(lang);
    }
  }, [language, setLanguage]);
}
