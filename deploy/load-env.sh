#!/bin/sh

# 必须用 source 执行，环境变量才会留在当前 Shell：
# . ./deploy/load-env.sh /opt/wifi-manager/backend.env

wifi_env_file=${WIFI_ENV_FILE:-${1:-}}
wifi_env_cr=$(printf '\r')
wifi_env_seen_keys=' '

if [ -z "$wifi_env_file" ]; then
    printf '必须显式传入仓库外环境文件路径，或设置 WIFI_ENV_FILE\n' >&2
    return 1 2>/dev/null || exit 1
fi

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
    case "$wifi_env_seen_keys" in
        *" $wifi_env_key "*)
            printf '环境文件包含重复变量：%s\n' "$wifi_env_key" >&2
            return 1 2>/dev/null || exit 1
            ;;
    esac
    wifi_env_seen_keys="$wifi_env_seen_keys$wifi_env_key "
    if [ "$wifi_env_key" = 'JWT_SECRET' ]; then
        printf 'JWT_SECRET 已停止使用，请改为配置 Nacos wifi-jwt.yml\n' >&2
        return 1 2>/dev/null || exit 1
    fi
    case "$wifi_env_value" in
        *CHANGE_ME*|*CHANGE-ME*|*change_me*|*change-me*|*your-frontend.example.com*)
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

wifi_env_required_keys='SPRING_PROFILES_ACTIVE MYSQL_URL MYSQL_USERNAME MYSQL_PASSWORD DB_MIGRATION_URL DB_MIGRATION_USERNAME DB_MIGRATION_PASSWORD DB_MIGRATION_BASELINE_ON_MIGRATE NACOS_SERVER_ADDR NACOS_USERNAME NACOS_PASSWORD NACOS_NAMESPACE NACOS_GROUP NACOS_DISCOVERY_IP WIFI_GATEWAY_TOKEN WIFI_INTERNAL_TOKEN WIFI_ALLOWED_ORIGIN WIFI_ALLOWED_ORIGIN_ALT WIFI_ALLOWED_ORIGINS WIFI_ALERT_HEARTBEAT_INTERVAL_MILLIS OAUTH_ALLOWED_RETURN_ORIGIN OAUTH_ALLOWED_RETURN_ORIGIN_ALT FORWARD_HEADERS_STRATEGY WIFI_TRUST_PROXY_HEADERS REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_DATABASE REDIS_SSL REDIS_RATE_LIMIT_ENABLED MQTT_BROKER_URL MQTT_CLIENT_ID MQTT_USERNAME MQTT_PASSWORD WIFI_COMMAND_SECRET_KEY WIFI_PAYMENT_DEFAULT_CHANNEL WIFI_PAYMENT_CALLBACK_WINDOW_SECONDS WIFI_PAYMENT_LOCAL_DEMO_SECRET WIFI_AVATAR_DIR'
for wifi_env_required_key in $wifi_env_required_keys; do
    case "$wifi_env_seen_keys" in
        *" $wifi_env_required_key "*) ;;
        *)
            printf '环境文件缺少必需变量：%s\n' "$wifi_env_required_key" >&2
            return 1 2>/dev/null || exit 1
            ;;
    esac
done

wifi_env_required_non_empty_keys='SPRING_PROFILES_ACTIVE MYSQL_URL MYSQL_USERNAME MYSQL_PASSWORD DB_MIGRATION_URL DB_MIGRATION_USERNAME DB_MIGRATION_PASSWORD NACOS_SERVER_ADDR NACOS_USERNAME NACOS_PASSWORD NACOS_GROUP WIFI_GATEWAY_TOKEN WIFI_INTERNAL_TOKEN WIFI_ALLOWED_ORIGIN WIFI_ALLOWED_ORIGIN_ALT WIFI_ALLOWED_ORIGINS WIFI_ALERT_HEARTBEAT_INTERVAL_MILLIS OAUTH_ALLOWED_RETURN_ORIGIN OAUTH_ALLOWED_RETURN_ORIGIN_ALT FORWARD_HEADERS_STRATEGY WIFI_TRUST_PROXY_HEADERS REDIS_HOST REDIS_PORT REDIS_DATABASE REDIS_SSL REDIS_RATE_LIMIT_ENABLED MQTT_BROKER_URL MQTT_CLIENT_ID WIFI_PAYMENT_DEFAULT_CHANNEL WIFI_PAYMENT_CALLBACK_WINDOW_SECONDS WIFI_PAYMENT_LOCAL_DEMO_SECRET WIFI_AVATAR_DIR'
for wifi_env_required_key in $wifi_env_required_non_empty_keys; do
    eval "wifi_env_required_value=\${$wifi_env_required_key-}"
    if [ -z "$wifi_env_required_value" ]; then
        printf '环境文件中的必需变量不能为空：%s\n' "$wifi_env_required_key" >&2
        return 1 2>/dev/null || exit 1
    fi
done

if [ "${SPRING_PROFILES_ACTIVE:-}" != 'prod' ]; then
    printf '正式部署必须设置 SPRING_PROFILES_ACTIVE=prod\n' >&2
    return 1 2>/dev/null || exit 1
fi
if [ "$DB_MIGRATION_BASELINE_ON_MIGRATE" != 'false' ]; then
    printf '正常生产部署必须设置 DB_MIGRATION_BASELINE_ON_MIGRATE=false\n' >&2
    return 1 2>/dev/null || exit 1
fi
if [ "$REDIS_RATE_LIMIT_ENABLED" != 'true' ]; then
    printf '生产部署必须设置 REDIS_RATE_LIMIT_ENABLED=true\n' >&2
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
case "$WIFI_GATEWAY_TOKEN" in
    [[:space:]]*|*[[:space:]])
        printf 'WIFI_GATEWAY_TOKEN 和 WIFI_INTERNAL_TOKEN 首尾不能包含空白字符\n' >&2
        return 1 2>/dev/null || exit 1
        ;;
esac
case "$WIFI_INTERNAL_TOKEN" in
    [[:space:]]*|*[[:space:]])
        printf 'WIFI_GATEWAY_TOKEN 和 WIFI_INTERNAL_TOKEN 首尾不能包含空白字符\n' >&2
        return 1 2>/dev/null || exit 1
        ;;
esac
if [ "$WIFI_ALLOWED_ORIGIN" = '*' ] || [ "$WIFI_ALLOWED_ORIGIN_ALT" = '*' ]; then
    printf '生产浏览器 Origin 配置不能包含 *\n' >&2
    return 1 2>/dev/null || exit 1
fi
wifi_env_old_ifs=$IFS
IFS=,
for wifi_env_origin in $WIFI_ALLOWED_ORIGINS; do
    wifi_env_origin=${wifi_env_origin#"${wifi_env_origin%%[![:space:]]*}"}
    wifi_env_origin=${wifi_env_origin%"${wifi_env_origin##*[![:space:]]}"}
    if [ "$wifi_env_origin" = '*' ]; then
        printf '生产浏览器 Origin 配置不能包含 *\n' >&2
        IFS=$wifi_env_old_ifs
        return 1 2>/dev/null || exit 1
    fi
done
IFS=$wifi_env_old_ifs

printf '已从 %s 加载后端环境变量到当前 Shell。\n' "$wifi_env_file"
unset wifi_env_file wifi_env_cr wifi_env_line wifi_env_key wifi_env_value wifi_env_seen_keys wifi_env_required_keys wifi_env_required_key wifi_env_required_non_empty_keys wifi_env_required_value wifi_env_old_ifs wifi_env_origin
