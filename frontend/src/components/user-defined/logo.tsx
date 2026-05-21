import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

interface LogoProp {
    avatarClassName?: string;
    avatarImageClassName?: string;
    src: string;
    width?: number;
    height?: number;
}

/**
 * Logo 組件
 * 
 * 顯示專案標誌頭像。支援自定義容器與圖片的樣式類別（Tailwind），並提供後備文字（YEET）。
 * 
 * @param props LogoProp 介面，包含 src, className 等
 * @returns 渲染後的 Avatar 組件
 */
const Logo = ({ avatarClassName, avatarImageClassName, src, width, height }: LogoProp) => {
    return (
        <Avatar className={avatarClassName}>
            <AvatarImage className={avatarImageClassName} src={src} width={width} height={height} />
            <AvatarFallback>YEET</AvatarFallback>
        </Avatar>
    )
}

export default Logo
