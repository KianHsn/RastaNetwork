/** Java `java.util.Random` LCG so map generation matches the desktop game. */
export class JavaRandom {
  constructor(seed = Date.now()) {
    this.setSeed(seed);
  }

  setSeed(seed) {
    this.seed = (toInt64(seed) ^ 0x5deece66dn) & ((1n << 48n) - 1n);
  }

  next(bits) {
    this.seed = (this.seed * 0x5deece66dn + 0xbn) & ((1n << 48n) - 1n);
    return Number(this.seed >> (48n - BigInt(bits)));
  }

  nextInt(bound) {
    if (bound === undefined) {
      return this.next(32) | 0;
    }
    if (bound <= 0) {
      throw new Error("bound must be positive");
    }
    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }
    let bits;
    let val;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (bits - val + (bound - 1) < 0);
    return val;
  }

  nextBoolean() {
    return this.next(1) !== 0;
  }

  nextLong() {
    return (BigInt(this.next(32) | 0) << 32n) + BigInt(this.next(32) | 0);
  }
}

export function shuffle(list, random) {
  for (let i = list.length; i > 1; i--) {
    const j = random.nextInt(i);
    const tmp = list[i - 1];
    list[i - 1] = list[j];
    list[j] = tmp;
  }
  return list;
}

function toInt64(value) {
  if (typeof value === "bigint") {
    return BigInt.asIntN(64, value);
  }
  return BigInt.asIntN(64, BigInt(Math.trunc(Number(value))));
}
