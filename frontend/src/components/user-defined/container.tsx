import { cn } from "@/lib/utils";

interface ContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

/**
 * 基礎佈局容器
 * 
 * 提供頁面置中且具備一致寬度（max-w-7xl）的容器。整合了標準的水平響應式 Padding。
 * 
 * @param props 繼承自 HTMLDivElement 的屬性，包含 children
 * @returns 渲染後的中心化布局容器
 */
export default function Container({ children, ...props }: ContainerProps) {
  return (
    <div className={cn("mx-auto w-full max-w-7xl px-4 md:px-6", props.className)} {...props}>
      {children}
    </div>
  );
}
