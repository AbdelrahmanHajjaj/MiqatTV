/* Runs offline in JavaScriptCore. The civil date comes from the prayer timezone. */
function miqatPrayerTimes(year, month, day, latitude, longitude, method, hanafi, highLatitude, offsets) {
  const factories = {
    EGYPTIAN: adhan.CalculationMethod.Egyptian,
    UMM_AL_QURA: adhan.CalculationMethod.UmmAlQura,
    MUSLIM_WORLD_LEAGUE: adhan.CalculationMethod.MuslimWorldLeague,
    KARACHI: adhan.CalculationMethod.Karachi,
    DUBAI: adhan.CalculationMethod.Dubai,
    KUWAIT: adhan.CalculationMethod.Kuwait,
    QATAR: adhan.CalculationMethod.Qatar,
    NORTH_AMERICA: adhan.CalculationMethod.NorthAmerica,
    MOON_SIGHTING_COMMITTEE: adhan.CalculationMethod.MoonsightingCommittee,
    SINGAPORE: adhan.CalculationMethod.Singapore,
    TURKEY: adhan.CalculationMethod.Turkey,
  };
  const keys = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const params = (factories[method.id] ?? adhan.CalculationMethod.Other)();
  if (method.preset) {
    params.fajrAngle = method.preset.fajr;
    params.ishaAngle = method.preset.isha;
    params.ishaInterval = method.preset.interval;
  }
  if (method.methodAdjustments) {
    params.methodAdjustments = Object.fromEntries(keys.map((key, i) => [key, method.methodAdjustments[i]]));
  }
  params.madhab = hanafi ? adhan.Madhab.Hanafi : adhan.Madhab.Shafi;
  params.highLatitudeRule = {
    middle: adhan.HighLatitudeRule.MiddleOfTheNight,
    seventh: adhan.HighLatitudeRule.SeventhOfTheNight,
    angle: adhan.HighLatitudeRule.TwilightAngle,
  }[highLatitude];
  params.rounding = adhan.Rounding.None;
  params.adjustments = Object.fromEntries(keys.map((key, i) => [key, offsets[i]]));
  const result = new adhan.PrayerTimes(
    new adhan.Coordinates(latitude, longitude), new Date(year, month - 1, day, 12), params,
  );
  const times = keys.map(key => result[key]?.getTime());
  return times.every(Number.isFinite) ? times : null;
}
