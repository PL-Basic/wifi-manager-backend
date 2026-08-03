#!/bin/sh

# 必须用 source 执行，环境变量才会留在当前 Shell：
# . ./deploy/load-env.sh /opt/wifi-manager/backend.env

wifi_env_file=${WIFI_ENV_FILE:-${1:-.env}}
wifi_env_cr=$(printf '\r')

if [ ! -r "$wifi_env_file" ]; then
    printf '无法读取环境文件：%s\n' "$wifi_env_file" >&2
    return 1 2>/dev/null || exit 1
fi

# 第一遍只校验格式和占位值，避免读到一半才发现模板尚未配置。
while IFS= read -r wifi_env_line || [ -n "$wifi_env_line" ]; do
    wifi_env_line=${wifi_env_line%"$wifi_env_cr"}
    wifi_env_line=${wifi_env_line#"${wifi_env_line%%[![:space:]]*}"}

    case "$wifi_env_line" in
        ''|'#'*) continue ;;
        *=*) ;;
        *)
            printf '环境文件包含无效行：%s\n' "$wifi_env_line" >&2
            return 1 2>/dev/null || exit 1
            ;;
    esac

    wifi_env_key=${wifi_env_line%%=*}
    wifi_env_value=${wifi_env_line#*=}
    case "$wifi_env_key" in
        ''|[0-9]*|*[!A-Za-z0-9_]*)
            printf '环境变量名无效：%s\n' "$wifi_env_key" >&2
            return 1 2>/dev/null || exit 1
            ;;
    esac
    case "$wifi_env_value" in
        *CHANGE_ME*|*your-frontend.example.com*)
            printf '环境变量仍是占位值：%s\n' "$wifi_env_key" >&2
            return 1 2>/dev/null || exit 1
            ;;
    esac
done < "$wifi_env_file"

while IFS= read -r wifi_env_line || [ -n "$wifi_env_line" ]; do
    wifi_env_line=${wifi_env_line%"$wifi_env_cr"}
    wifi_env_line=${wifi_env_line#"${wifi_env_line%%[![:space:]]*}"}
    case "$wifi_env_line" in
        ''|'#'*) continue ;;
    esac

    wifi_env_key=${wifi_env_line%%=*}
    wifi_env_value=${wifi_env_line#*=}
    export "$wifi_env_key=$wifi_env_value"
done < "$wifi_env_file"

if [ "${SPRING_PROFILES_ACTIVE:-}" != 'prod' ]; then
    printf '正式部署必须设置 SPRING_PROFILES_ACTIVE=prod\n' >&2
    return 1 2>/dev/null || exit 1
fi
if [ ${#JWT_SECRET} -lt 32 ]; then
    printf 'JWT_SECRET 不得少于 32 个字符\n' >&2
    return 1 2>/dev/null || exit 1
fi
if [ ${#WIFI_GATEWAY_TOKEN} -lt 16 ] || [ ${#WIFI_INTERNAL_TOKEN} -lt 16 ]; then
    printf 'WIFI_GATEWAY_TOKEN 和 WIFI_INTERNAL_TOKEN 均不得少于 16 个字符\n' >&2
    return 1 2>/dev/null || exit 1
fi
if [ "$WIFI_GATEWAY_TOKEN" = "$WIFI_INTERNAL_TOKEN" ]; then
    printf 'WIFI_GATEWAY_TOKEN 和 WIFI_INTERNAL_TOKEN 必须使用不同值\n' >&2
    return 1 2>/dev/null || exit 1
fi

printf '已从 %s 加载后端环境变量到当前 Shell。\n' "$wifi_env_file"
unset wifi_env_file wifi_env_cr wifi_env_line wifi_env_key wifi_env_value
