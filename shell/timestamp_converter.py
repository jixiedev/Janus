#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
时间戳转换脚本
功能：将yyyyMMdd格式的日期字符串转换为Unix时间戳（秒）

作者：SenseNova AI Assistant
日期：2025-06-17
"""

import sys
from datetime import datetime
import pytz


class DateConversionError(Exception):
    """自定义日期转换异常类"""
    pass


def validate_date_string(date_str):
    """
    验证日期字符串格式
    
    参数:
        date_str: 待验证的日期字符串
    
    返回:
        bool: 验证是否通过
    
    异常:
        DateConversionError: 验证失败时抛出
    """
    # 检查是否为空或None
    if not date_str or date_str.strip() == '':
        raise DateConversionError("日期字符串不能为空")
    
    # 去除空格
    date_str = date_str.strip()
    
    # 检查长度
    if len(date_str) != 8:
        raise DateConversionError(f"日期字符串长度应为8位，当前为{len(date_str)}位")
    
    # 检查是否全为数字
    if not date_str.isdigit():
        raise DateConversionError("日期字符串应全为数字，不能包含字母或特殊字符")
    
    # 提取年、月、日
    try:
        year = int(date_str[:4])
        month = int(date_str[4:6])
        day = int(date_str[6:8])
    except ValueError as e:
        raise DateConversionError(f"无法解析年、月、日：{str(e)}")
    
    # 验证年份范围（Unix时间戳从1970年开始）
    if year < 1970:
        raise DateConversionError(f"年份不能小于1970（Unix时间戳起始年），当前为{year}")
    
    if year > 2100:
        raise DateConversionError(f"年份不能大于2100，当前为{year}")
    
    # 验证月份
    if month < 1 or month > 12:
        raise DateConversionError(f"月份应在1-12之间，当前为{month}")
    
    # 验证日期
    if day < 1 or day > 31:
        raise DateConversionError(f"日期应在1-31之间，当前为{day}")
    
    # 进一步验证日期的有效性（如2月30日等）
    try:
        datetime(year, month, day)
    except ValueError as e:
        raise DateConversionError(f"日期无效：{str(e)}")
    
    return True


def parse_date_string(date_str):
    """
    解析日期字符串
    
    参数:
        date_str: 格式为yyyyMMdd的字符串
    
    返回:
        datetime对象
    
    异常:
        DateConversionError: 当日期格式不正确时抛出
    """
    # 先验证格式
    validate_date_string(date_str)
    
    # 提取年、月、日
    year = int(date_str[:4])
    month = int(date_str[4:6])
    day = int(date_str[6:8])
    
    # 创建datetime对象（默认时间为00:00:00）
    dt = datetime(year, month, day)
    
    return dt


def validate_timezone(timezone_str):
    """
    验证时区名称
    
    参数:
        timezone_str: 时区名称字符串
    
    返回:
        pytz timezone对象
    
    异常:
        DateConversionError: 时区无效时抛出
    """
    try:
        tz = pytz.timezone(timezone_str)
        return tz
    except pytz.exceptions.UnknownTimeZoneError:
        raise DateConversionError(f"未知的时区：{timezone_str}")
    except Exception as e:
        raise DateConversionError(f"时区验证失败：{str(e)}")


def convert_to_timestamp(date_str, timezone='UTC'):
    """
    将日期字符串转换为Unix时间戳
    
    参数:
        date_str: 格式为yyyyMMdd的字符串
        timezone: 时区名称，默认UTC
    
    返回:
        Unix时间戳（秒）
    
    异常:
        DateConversionError: 当日期格式不正确或时区无效时抛出
    """
    # 验证时区
    tz = validate_timezone(timezone)
    
    # 解析日期
    dt = parse_date_string(date_str)
    
    # 设置时区
    dt = tz.localize(dt)
    
    # 转换为Unix时间戳（秒）
    timestamp = int(dt.timestamp())
    
    return timestamp


def convert_to_timestamp_simple(date_str):
    """
    简化的时间戳转换函数（使用UTC时区）
    
    参数:
        date_str: 格式为yyyyMMdd的字符串
    
    返回:
        Unix时间戳（秒）
    """
    return convert_to_timestamp(date_str, 'UTC')


def main():
    """主函数"""
    # 检查命令行参数
    if len(sys.argv) < 2:
        print("用法: python timestamp_converter.py <日期字符串>")
        print("示例: python timestamp_converter.py 20250617")
        print("提示: 日期格式应为yyyyMMdd")
        print("")
        print("可用选项:")
        print("  --timezone, -t  指定时区（默认UTC）")
        print("  --help, -h      显示帮助信息")
        sys.exit(1)
    
    # 解析参数
    date_str = None
    timezone = 'UTC'
    
    i = 1
    while i < len(sys.argv):
        if sys.argv[i] in ['--help', '-h']:
            print("时间戳转换工具")
            print("用法: python timestamp_converter.py [选项] <日期字符串>")
            print("")
            print("参数:")
            print("  日期字符串    格式为yyyyMMdd的日期")
            print("")
            print("选项:")
            print("  --timezone, -t  指定时区（默认UTC）")
            print("  --help, -h      显示帮助信息")
            print("")
            print("示例:")
            print("  python timestamp_converter.py 20250617")
            print("  python timestamp_converter.py -t Asia/Shanghai 20250617")
            sys.exit(0)
        elif sys.argv[i] in ['--timezone', '-t']:
            if i + 1 < len(sys.argv):
                timezone = sys.argv[i + 1]
                i += 2
            else:
                print("错误: --timezone 需要指定时区名称")
                sys.exit(1)
        else:
            if date_str is None:
                date_str = sys.argv[i]
            else:
                print(f"错误: 多余的参数 '{sys.argv[i]}'")
                sys.exit(1)
            i += 1
    
    if date_str is None:
        print("错误: 必须提供日期字符串")
        sys.exit(1)
    
    try:
        # 转换时间戳
        timestamp = convert_to_timestamp(date_str, timezone)
        
        # 输出结果
        print(f"输入日期: {date_str}")
        print(f"时区: {timezone}")
        print(f"Unix时间戳: {timestamp}")
        
        # 显示对应的时间
        dt_utc = datetime.utcfromtimestamp(timestamp)
        print(f"对应UTC时间: {dt_utc.strftime('%Y-%m-%d %H:%M:%S')} UTC")
        
        # 显示对应时区的时间
        tz = pytz.timezone(timezone)
        dt_local = dt_utc.replace(tzinfo=pytz.utc).astimezone(tz)
        print(f"对应{timezone}时间: {dt_local.strftime('%Y-%m-%d %H:%M:%S')} {timezone}")
        
    except DateConversionError as e:
        print(f"错误: {str(e)}")
        sys.exit(1)
    except Exception as e:
        print(f"未知错误: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()
