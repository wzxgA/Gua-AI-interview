import { useTranslation } from 'react-i18next';

type EnumNamespace =
  | 'category'
  | 'difficulty'
  | 'parseStatus'
  | 'sessionStatus'
  | 'persona'
  | 'tier'
  | 'positionStatus';

/** 获取枚举值的本地化标签，如 useEnumLabel()('sessionStatus', 'IN_PROGRESS') → '进行中' */
export function useEnumLabel() {
  const { t } = useTranslation();
  return (ns: EnumNamespace, value: string | undefined | null, fallback?: string) => {
    if (!value) return fallback ?? '';
    const key = `common.${ns}.${value}`;
    const result = t(key);
    return result === key ? fallback ?? value : result;
  };
}
