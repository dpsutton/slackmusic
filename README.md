# slackmusic

Slackbot listening to link to spotify (for now)

## Running

Create a config.edn from the confid.edn.template file. This dictates
the api key for the slackbot and the prefix that will manually trigger
it. Currently the prefix is "musicbot" and the key can be found in the
slack integration settings. Once this is set, just type `lein run` and
you should be up and running.

## What it does

Slackmusic has a sockets pipe of every channel it is invited to. It
inspects every message, sees if it can handle it, and then
does. That's it. It currently handles the youtube expanded links that
slack "unfurls" and commands `musicbot artist title`.

Its current "handling" of this is to just query the slack api for
tracks on the search term, taking only 1, and then returning that link
to the channel that issued the command.

## How to Extend

You need to tell the infrastructure that it can handle the input in
comms.slack_rtm.clj/can-handle?. This is just a big or where each
function normalizes the input into a map containing the originating
command and the title to search for (eg,

    { :type :youtube-preview
      :title "rick astley never gonna give you up" }

This is thrown in a channel where there is a super simple multimethod
dispatch chain. The multimethods title->link is a generic method that
works on the type, although they all (for now) just get spotify links.

    {:service :spotify :title title
    :origin :command :url "https://someurl/"}

The second multimethod in this chain is get-data! which takes this
created map and dispatches according to the service. Since there is
only spotify at the moment, there is only one implementation but this
should make it easy to include future services.

So for instance, now, it queries the spotify api and parses out a url
from the big json response that we can shoot back to the
channel. There is no api for Google play music so we might not to need
to get data for it, so we just generate a search url and this would
(hopefully) just open the persons browser to a search on google play
music.

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
