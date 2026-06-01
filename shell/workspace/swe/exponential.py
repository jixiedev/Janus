#!/usr/bin/env python3
"""
Exponential Calculator
Calculates the power of a base number raised to an exponent.
"""

def calculate_exponent(base, exponent):
    """
    Calculate base raised to the power of exponent.
    
    Args:
        base: The base number
        exponent: The exponent
    
    Returns:
        The result of base ** exponent
    """
    return base ** exponent


if __name__ == "__main__":
    import argparse
    
    # Create argument parser
    parser = argparse.ArgumentParser(
        description="Calculate the power of a base number raised to an exponent."
    )
    parser.add_argument(
        "base",
        type=float,
        help="The base number"
    )
    parser.add_argument(
        "exponent",
        type=float,
        help="The exponent"
    )
    
    # Parse arguments
    args = parser.parse_args()
    
    # Calculate and display result
    result = calculate_exponent(args.base, args.exponent)
    print(f"{args.base}^{args.exponent} = {result}")
