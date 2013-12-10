   let $w := ./weather
   return $w/station = 'Raleigh-Durham International Airport (KRDU)'
      and $w/temperature_f > 50
      and $w/temperature_f - $w/dewpoint > 5
      and $w/wind_speed_mph > 7
      and $w/wind_speed_mph < 20
