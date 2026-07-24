import { useCallback, useMemo, useState } from 'react'
import { getToken } from './api/auth'
import type { Channel } from './catalog/rules'
import { HomeScreen, type HomeSection } from './screens/Home'
import { LoginScreen } from './screens/Login'
import { PlayerScreen } from './screens/Player'
import { SplashScreen } from './screens/Splash'

type Route =
  | { name: 'splash' }
  | { name: 'login' }
  | { name: 'home'; section: HomeSection }
  | { name: 'player'; channel: Channel; neighbors: Channel[] }

export default function App() {
  const hasSession = useMemo(() => !!getToken(), [])
  const [route, setRoute] = useState<Route>({ name: 'splash' })

  const finishSplash = useCallback(() => {
    setRoute(hasSession || getToken() ? { name: 'home', section: 'hub' } : { name: 'login' })
  }, [hasSession])

  if (route.name === 'splash') {
    return <SplashScreen onDone={finishSplash} />
  }

  if (route.name === 'login') {
    return (
      <LoginScreen
        onLoggedIn={() => setRoute({ name: 'home', section: 'hub' })}
      />
    )
  }

  if (route.name === 'player') {
    return (
      <PlayerScreen
        channel={route.channel}
        neighbors={route.neighbors}
        onBack={() => setRoute({ name: 'home', section: 'live' })}
        onChange={(ch) =>
          setRoute({ name: 'player', channel: ch, neighbors: route.neighbors })
        }
      />
    )
  }

  return (
    <HomeScreen
      section={route.section}
      onSection={(section) => setRoute({ name: 'home', section })}
      onPlay={(channel, neighbors) => setRoute({ name: 'player', channel, neighbors })}
      onLogout={() => setRoute({ name: 'login' })}
    />
  )
}
