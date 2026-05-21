import { motion } from "motion/react";
import type { HTMLMotionProps, Variants } from "motion/react";
import { cn } from "@/lib/utils";

/**
 * 全域動效包裝組件
 * 
 * 用於封裝 Framer Motion 的動畫邏輯，提供統一的頁面進入與元素顯現效果。
 * 支援「容器級 (Stagger)」與「項目級 (Item)」兩種動畫預設。
 */

// 容器動畫：負責淡入、升起，並觸發子元素的交錯動畫
const containerVariants: Variants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.6,
      staggerChildren: 0.1,
    },
  },
};

// 項目動畫：單一元素的小幅度淡入與升起
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 10 },
  visible: { 
    opacity: 1, 
    y: 0, 
    transition: { duration: 0.4 } 
  },
};

interface MotionWrapperProps extends HTMLMotionProps<"div"> {
  /** 動畫類型：container (容器並發) 或 item (單一項目) */
  variantsType?: "container" | "item";
  children: React.ReactNode;
}

/**
 * MotionWrapper 組件
 * 
 * @param variantsType 預設動畫類型
 * @param className 額外的 CSS 類別
 * @param children 子元素
 * @returns 帶有動畫效果的容器
 */
export const MotionWrapper = ({ 
  children, 
  variantsType = "item", 
  className,
  initial,
  animate,
  ...props 
}: MotionWrapperProps) => {
  const variants = variantsType === "container" ? containerVariants : itemVariants;
  
  // 核心邏輯：
  // 1. 如果是 container，預設必須給予啟動狀態 (hidden -> visible) 才能觸發 stagger。
  // 2. 如果是 item，預設不給予狀態 (undefined)，使其自動繼承父層 (variantsType="container") 的狀態。
  // 3. 允許開發者透過 props 強制覆蓋行為。
  const defaultInitial = variantsType === "container" ? "hidden" : undefined;
  const defaultAnimate = variantsType === "container" ? "visible" : undefined;

  return (
    <motion.div
      initial={initial ?? defaultInitial}
      animate={animate ?? defaultAnimate}
      variants={variants}
      className={cn(className)}
      {...props}
    >
      {children}
    </motion.div>
  );
};
