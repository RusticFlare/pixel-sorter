# pixel-sorter

[![npm](https://img.shields.io/npm/v/@rusticflare/pixel-sorter)](https://www.npmjs.com/package/@rusticflare/pixel-sorter)
[![GitHub](https://img.shields.io/github/license/RusticFlare/pixel-sorter)](LICENSE)
[![GitHub Sponsors](https://img.shields.io/github/sponsors/RusticFlare?style=social)](https://github.com/sponsors/RusticFlare)

A command line app for pixel sorting images

## Installation

1. [Install NPM](https://www.npmjs.com/get-npm)
1. Install `pixel-sorter`
    ```shell
    npm i -g @rusticflare/pixel-sorter
    ```

## Usage

### Pixel Sort Your First Image

This will pixel sort an image called `example.jpg` in your current directory and save the output in the same directory
as `example.jpg`.

```shell
pixel-sorter example.jpg
```

**Note:** A one pixel border is cropped from the output file (this is due to this pixel sorter making some of those
outer pixels slightly transparent)

<details><summary>Troubleshooting</summary>

- **Powershell**
    - Make sure you are running as an _Administrator_
    - If you see this error:
      ```shell
      ... cannot be loaded because the execution of scripts is disabled on this system.
      ```
      run:
      ```shell
      Set-ExecutionPolicy RemoteSigned
      ```
      ([See this StackOverflow answer for more details](https://stackoverflow.com/a/4038991))

</details>

### Options

_See [Examples](#examples) for how to use these_

|Option|Argument|Description|Default Value|
|---|---|---|---|
|`-o`|Text|The name of the output file (e.g. `sorted`)|The current date and time|
|`-m`|File|The path to "mask" file|N/A|
|`-a`|A number in `0.0`-`360.0`|The angle to sort along (as degrees on a compass)|`0.0`|
|`-i`|[An interval function](#interval-functions)|The interval function to use|`lightness`|
|`-l`|A number in `0.0`-`1.0`|The darkest `lightness` to include in sorted sections (used when the interval function is `lightness`)|`0.25`|
|`-u`|A number in `0.0`-`1.0`|The brightest `lightness` to include in sorted sections (used when the interval function is `lightness`)|`0.8`|
|`-w`|A positive whole number|The average width (in pixels) of the `random` sorted sections (used when the interval function is `random`)|`400`|
|`-s`|[A sorting function](#sorting-functions)|The sorting function to use|`lightness`|
|`-f`|[A filetype](#filetypes)|The filetype to output|`jpg`|
|`-h`|N/A|Print the help message|N/A|

### Interval Functions

- `lightness` - pixels with a `lightness` between the `-l` and `-u` values are sorted
- `random` - random sections of average width `-w` are sorted
- `none` - everything is sorted

### Sorting Functions

- `hue`
- `saturation`
- `lightness`
- `intensity`

### Filetypes

- `jpg`
- `png`

## Examples

`example.jpg`:
![](examples/example.jpg)

### Default

```shell
pixel-sorter example.jpg
```

![](examples/example-sorted-default.jpg)

### Threshold

When using the `lightness` interval function: only pixels with a `lightness` between `-l` and `-u` will be sorted.

Here we sort pixels with a `lightness` between 0.6 and 0.9:

```shell
pixel-sorter example.jpg -l 0.6 -u 0.9
```

![](examples/example-sorted-l-6-u-9.jpg)

### Angle 🧭

You can change the sorting angle (as degrees on a compass).

```shell
pixel-sorter example.jpg -a 315
```

![](examples/example-sorted-a-315.jpg)

### Mask & Random 🎭

A "mask" file should be a black and white image (the same size as the sorted image). Only the white sections are
considered for sorting.

`-i random` causes random sections of the image should be sorted. You can control the average width (in pixels) of these
sections with `-w`.

`example-mask.jpg`:

![](examples/example-mask.jpg)

```shell
pixel-sorter example.jpg -m example-mask.jpg -a 135 -i random
```

![](examples/example-sorted-mask.jpg)

## Updating

```shell
npm up -g @rusticflare/pixel-sorter
```
