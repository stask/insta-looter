# insta-looter

insta-looter is a clojure library that allows to retrieve Instagram posts without using their API. It was inspired by [InstaLooter](https://github.com/althonos/InstaLooter) Python library.

Instagram API is very limited (at least until they approve your application). I just needed to access some public posts, this library does just that.

**Warning: since this library doesn't use API, it might stop working if Instagram changes how their website works.**

### Current version:

[![Clojars Project](https://img.shields.io/clojars/v/insta-looter.svg)](https://clojars.org/insta-looter)

## Usage

### Retrieve user profile and n latest posts

Will return a hashmap with the profile information and `n` latest posts in `:media` key of the profile.

It will automatically retrieve multiple pages if needed, depending on how many posts you want to get.

``` clojure
(require '[insta-looter.core :as looter])

(pprint (looter/loot-profile "justinbieber" 2))

```

### Retrieve individual post

The `code` parameter is the post identifier (the link for individual post looks like this: "https://www.instagram.com/p/BSMpnRHhvJv/", where "BSMpnRHhvJv" is the code).

Will return a hashmap with two keys: `:profile` (with some limited profile information) and `:post` with the post details including comments.

``` clojure
(require '[insta-looter.core :as looter])

(pprint (looter/loot-post "BSMpnRHhvJv"))
```

### Search users

This function uses Instagram's auto-complete to perform the search since they don't have a proper search. You can't really control anything here, but it's better than nothing.
Returns a list of user profile hashmaps.

``` clojure
(require '[insta-looter.core :as looter])

(pprint (looter/search-users "southpark"))
```

## License

Copyright © 2017 stask.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
